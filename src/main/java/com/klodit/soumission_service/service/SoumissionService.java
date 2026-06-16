package com.klodit.soumission_service.service;

import com.klodit.soumission_service.client.AppelOffreClient;
import com.klodit.soumission_service.client.DocumentsClient;
import com.klodit.soumission_service.client.UtilisateurClient;
import com.klodit.soumission_service.client.dto.AppelOffreExterneDTO;
import com.klodit.soumission_service.client.dto.LotExterneDTO;
import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
import com.klodit.soumission_service.dto.response.AnomaliesParAoResponse;
import com.klodit.soumission_service.dto.response.SoumissionDetailResponse;
import com.klodit.soumission_service.dto.response.SoumissionResponse;
import com.klodit.soumission_service.dto.response.LigneOffreFinanciereResponse;
import com.klodit.soumission_service.entity.LigneOffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.OffreDejaDeposeeException;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.messaging.event.SoumissionDeposeeEvent;
import com.klodit.soumission_service.messaging.event.SoumissionRecueEvent;
import com.klodit.soumission_service.messaging.event.SoumissionStatutChangeEvent;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import com.klodit.soumission_service.repository.CautionRepository;
import com.klodit.soumission_service.repository.LigneOffreFinanciereRepository;
import com.klodit.soumission_service.repository.OffreFinanciereRepository;
import com.klodit.soumission_service.repository.OffreTechniqueRepository;
import com.klodit.soumission_service.repository.AnomalieIaRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import com.klodit.soumission_service.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SoumissionService {

        private final SoumissionRepository soumissionRepository;
        private final OffreTechniqueRepository offreTechniqueRepository;
        private final OffreFinanciereRepository offreFinanciereRepository;
        private final CautionRepository cautionRepository;
        private final LigneOffreFinanciereRepository ligneOffreFinanciereRepository;
        private final HorodatageService horodatageService;
        private final AuditLogService auditLogService;
        private final SoumissionEventPublisher eventPublisher;
        private final AppelOffreClient appelOffreClient;
        private final UtilisateurClient utilisateurClient;
        private final DocumentsClient documentsClient;
        private final AnomalieIaRepository anomalieIaRepository;

        /**
         * US-1 : Créer une soumission en brouillon
         */
        @Transactional
        public SoumissionResponse creerBrouillon(CreateSoumissionRequest request, String operateurId) {
                log.info("Création brouillon — AO: {}, OE: {}", request.getAppelOffreId(), operateurId);

                // Vérifier unicité (un OE ne soumet qu'une fois par AO + lot)
                soumissionRepository.findByAppelOffreIdAndOperateurIdAndLotId(
                                request.getAppelOffreId(), operateurId, request.getLotId()).ifPresent(s -> {
                                        throw new OffreDejaDeposeeException(
                                                        "soumission pour cet appel d'offres et ce lot");
                                });

                // Vérifier que l'AO existe et est en statut PUBLIE
                java.util.Optional<AppelOffreExterneDTO> aoOpt = appelOffreClient
                                .getAppelOffre(request.getAppelOffreId());
                if (aoOpt.isEmpty() || !"PUBLIE".equalsIgnoreCase(aoOpt.get().getStatut())) {
                        log.warn("Tentative de soumission sur AO non existant ou non publié : {}",
                                        request.getAppelOffreId());
                        // Dégradation gracieuse : si le service AO est indisponible, on autorise
                        // la création du brouillon car la vérification sera faite à la validation
                        // (US-5)
                }

                Soumission soumission = Soumission.builder()
                                .appelOffreId(request.getAppelOffreId())
                                .operateurId(operateurId)
                                .lotId(request.getLotId())
                                .reference(ReferenceGenerator.generate())
                                .statut(StatutSoumission.BROUILLON)
                                .isElectronique(true)
                                .build();

                final Soumission savedSoumission = soumissionRepository.save(soumission);
                log.info("Brouillon créé — ID: {}, Ref: {}", savedSoumission.getId(), savedSoumission.getReference());

                // Pré-peuplage du BPU Financier
                if (aoOpt.isPresent() && aoOpt.get().getLots() != null) {
                        List<LotExterneDTO> lots = aoOpt.get().getLots();
                        if (request.getLotId() != null) {
                                // Filtrer le lot concerné par la soumission
                                String targetLotId = request.getLotId();
                                lots.stream()
                                                .filter(lot -> targetLotId.equals(lot.getId()))
                                                .findFirst()
                                                .ifPresent(lot -> creerLigneBpu(savedSoumission, lot));
                        } else {
                                // Soumission globale : pré-charger tous les lots
                                lots.forEach(lot -> creerLigneBpu(savedSoumission, lot));
                        }
                } else {
                        log.warn("Pré-peuplage BPU impossible pour la soumission {} : Service AO indisponible ou aucun lot défini",
                                        savedSoumission.getId());
                }

                return toResponse(savedSoumission);
        }

        private void creerLigneBpu(Soumission soumission, LotExterneDTO lot) {
                LigneOffreFinanciere ligne = LigneOffreFinanciere.builder()
                                .soumission(soumission)
                                .designation(lot.getDesignation())
                                .quantite(java.math.BigDecimal.ONE)
                                .unite("LOT")
                                .prixUnitaire(null)
                                .build();
                ligneOffreFinanciereRepository.save(ligne);
                log.info("Ligne BPU pré-peuplée pour lot {} — ID Ligne: {}", lot.getId(), ligne.getId());
        }

        /**
         * US-8 : Lister les soumissions d'un opérateur économique
         */
        public List<SoumissionResponse> listerMesSoumissions(String operateurId) {
                return soumissionRepository.findByOperateurIdOrderByCreatedAtDesc(operateurId)
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        /**
         * Consulter le détail d'une soumission
         */
        public SoumissionDetailResponse getDetail(String id) {
                Soumission s = soumissionRepository.findById(id)
                                .orElseThrow(() -> new SoumissionNotFoundException(id));
                return toDetailResponse(s);
        }

        /**
         * Lister les soumissions d'un appel d'offres
         */
        public List<SoumissionResponse> listerParAppelOffre(String appelOffreId) {
                return soumissionRepository.findByAppelOffreIdOrderByCreatedAtDesc(appelOffreId)
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        /**
         * US-5 : Valider et soumettre définitivement la soumission.
         * - Vérifie que l'offre technique, financière et la caution sont présentes
         * - Horodate le dépôt (timestamp légal serveur)
         * - Vérifie que l'on est dans le délai (appel AO Service)
         * - Publie l'événement soumission.deposee
         */
        @Transactional
        public SoumissionResponse validerEtSoumettre(
                        String soumissionId, String operateurId, String ipDepot) {

                Soumission soumission = soumissionRepository.findById(soumissionId)
                                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));

                if (!soumission.getOperateurId().equals(operateurId)) {
                        throw new com.klodit.soumission_service.exception.AccesRefuseException(
                                        "Accès refusé : vous n'êtes pas le propriétaire de cette soumission");
                }

                if (soumission.getStatut() != StatutSoumission.BROUILLON) {
                        throw new IllegalStateException(
                                        "Seules les soumissions en BROUILLON peuvent être soumises. " +
                                                        "Statut actuel : " + soumission.getStatut());
                }

                // Vérifier la complétude du dossier
                verifierCompletudeDossier(soumissionId);

                // Vérifier que l'opérateur est un OE valide et vérifié
                if (!utilisateurClient.isOperateurValide(operateurId)) {
                        auditLogService.logValidation(soumissionId, operateurId, ipDepot,
                                        false, "Opérateur non valide ou non vérifié");
                        throw new IllegalStateException(
                                        "Votre profil opérateur économique n'est pas vérifié. "
                                                        + "Contactez l'administration pour valider votre compte.");
                }

                // Horodater côté serveur (timestamp légal — fait foi légale Loi 23-12)
                LocalDateTime horodatage = horodatageService.maintenant();

                // Vérifier le délai légal via le Service Appel d'Offres (REST synchrone)
                boolean dansDelai = appelOffreClient.getDateLimiteDepot(soumission.getAppelOffreId())
                                .map(dateLimite -> horodatageService.estDansDelai(dateLimite))
                                .orElseGet(() -> {
                                        log.warn("Service AO indisponible — délai non vérifié pour soumission {}",
                                                        soumissionId);
                                        return true; // Dégradation gracieuse : autoriser, le service AO vérifiera après
                                });

                // ⚠️ Vérifier le délai AVANT de sauvegarder et publier l'événement
                if (!dansDelai) {
                        auditLogService.logValidation(soumissionId, operateurId, ipDepot,
                                        false, "Hors délai — horodatage: " + horodatageService.formater(horodatage));
                        throw new com.klodit.soumission_service.exception.DelaiDepotExpireException(
                                        soumission.getAppelOffreId());
                }

                soumission.setStatut(StatutSoumission.DEPOSEE);
                soumission.setHorodatageServeur(horodatage);
                soumission.setIpDepot(ipDepot);
                soumission.setIsDansDelai(dansDelai);

                soumission = soumissionRepository.save(soumission);

                log.info("Soumission validée et déposée — ID: {}, Ref: {}, Horodatage: {}, Dans délai: {}",
                                soumission.getId(), soumission.getReference(), horodatage, dansDelai);

                // Log d'audit
                auditLogService.logValidation(soumissionId, operateurId, ipDepot,
                                true, horodatageService.formater(horodatage));

                // Publier l'événement soumission.deposee (asynchrone)
                eventPublisher.publierSoumissionDeposee(
                                SoumissionDeposeeEvent.builder()
                                                .soumissionId(soumission.getId())
                                                .reference(soumission.getReference())
                                                .appelOffreId(soumission.getAppelOffreId())
                                                .operateurId(soumission.getOperateurId())
                                                .lotId(soumission.getLotId())
                                                .horodatageServeur(horodatage)
                                                .isDansDelai(dansDelai)
                                                .build());

                // Publier l'accusé de réception (asynchrone)
                String accuseRef = "AR-" + soumission.getReference() + "-"
                                + horodatage.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                eventPublisher.publierSoumissionRecue(
                                SoumissionRecueEvent.builder()
                                                .soumissionId(soumission.getId())
                                                .reference(soumission.getReference())
                                                .appelOffreId(soumission.getAppelOffreId())
                                                .operateurId(soumission.getOperateurId())
                                                .horodatageReception(horodatage)
                                                .accuseReceptionRef(accuseRef)
                                                .build());

                return toResponse(soumission);
        }

        /**
         * Mettre à jour le statut d'une soumission (workflow interne / commission).
         * 
         */
        // ── Transitions de statut autorisées ──────────────
        private static final java.util.Map<StatutSoumission, java.util.Set<StatutSoumission>> TRANSITIONS_VALIDES = java.util.Map
                        .of(
                                        StatutSoumission.BROUILLON, java.util.Set.of(StatutSoumission.DEPOSEE),
                                        StatutSoumission.DEPOSEE, java.util.Set.of(StatutSoumission.RECUE),
                                        StatutSoumission.RECUE, java.util.Set.of(StatutSoumission.OUVERTE),
                                        StatutSoumission.OUVERTE, java.util.Set.of(StatutSoumission.EVALUEE),
                                        StatutSoumission.EVALUEE,
                                        java.util.Set.of(StatutSoumission.RETENUE, StatutSoumission.REJETEE));

        @Transactional
        public SoumissionResponse changerStatut(String soumissionId, StatutSoumission nouveauStatut) {
                Soumission soumission = soumissionRepository.findById(soumissionId)
                                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));

                StatutSoumission statutActuel = soumission.getStatut();
                java.util.Set<StatutSoumission> statutsPermis = TRANSITIONS_VALIDES.getOrDefault(
                                statutActuel, java.util.Set.of());

                if (!statutsPermis.contains(nouveauStatut)) {
                        throw new IllegalStateException(
                                        "Transition de statut invalide : " + statutActuel + " → " + nouveauStatut
                                                        + ". Transitions autorisées depuis " + statutActuel + " : "
                                                        + statutsPermis);
                }

                log.info("Changement statut soumission {} : {} → {}",
                                soumissionId, statutActuel, nouveauStatut);

                soumission.setStatut(nouveauStatut);
                soumission = soumissionRepository.save(soumission);

                // Publier l'événement de changement de statut (routing key dynamique)
                eventPublisher.publierStatutChange(
                                SoumissionStatutChangeEvent.builder()
                                                .soumissionId(soumissionId)
                                                .reference(soumission.getReference())
                                                .appelOffreId(soumission.getAppelOffreId())
                                                .ancienStatut(statutActuel.name())
                                                .nouveauStatut(nouveauStatut.name())
                                                .horodatage(LocalDateTime.now())
                                                .declenchePar("SYSTEM")
                                                .build());

                // Déclencher l'analyse IA (détection d'anomalies) lors de l'ouverture des plis
                if (nouveauStatut == StatutSoumission.OUVERTE) {
                        eventPublisher.publierSoumissionsClosed(soumission.getAppelOffreId());
                }

                return toResponse(soumission);
        }

        // ── Helpers privés ─────────────────────────────────

        private void verifierCompletudeDossier(String soumissionId) {
                // Vérifier présence offre technique
                offreTechniqueRepository.findBySoumissionId(soumissionId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "L'offre technique est manquante. Impossible de soumettre."));

                // Vérifier présence offre financière
                offreFinanciereRepository.findBySoumissionId(soumissionId)
                                .orElseThrow(() -> new IllegalStateException(
                                                "L'offre financière est manquante. Impossible de soumettre."));

                // Charger la soumission pour récupérer l'appelOffreId
                Soumission soumission = soumissionRepository.findById(soumissionId)
                                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));

                // Vérifier présence caution (obligatoire si l'AO l'exige)
                boolean cautionRequise = appelOffreClient.isCautionRequise(soumission.getAppelOffreId());
                if (cautionRequise && cautionRepository.findBySoumissionId(soumissionId).isEmpty()) {
                        throw new IllegalStateException(
                                        "La caution bancaire est obligatoire pour cet appel d'offres. Impossible de soumettre.");
                }
                if (!cautionRequise && cautionRepository.findBySoumissionId(soumissionId).isEmpty()) {
                        log.info("Caution absente mais non exigée par l'AO — soumission autorisée");
                }

                // Vérifier les pièces administratives via le Service Documents
                if (!documentsClient.arePiecesAdministrativesValides(soumissionId)) {
                        throw new IllegalStateException(
                                        "Les pièces administratives sont incomplètes ou non validées. Impossible de soumettre.");
                }
        }

        // ── Mappers privés ─────────────────────────────

        private SoumissionResponse toResponse(Soumission s) {
                return SoumissionResponse.builder()
                                .id(s.getId())
                                .appelOffreId(s.getAppelOffreId())
                                .operateurId(s.getOperateurId())
                                .lotId(s.getLotId())
                                .reference(s.getReference())
                                .statut(s.getStatut())
                                .horodatageServeur(s.getHorodatageServeur())
                                .isElectronique(s.getIsElectronique())
                                .isDansDelai(s.getIsDansDelai())
                                .ipDepot(s.getIpDepot())
                                .createdAt(s.getCreatedAt())
                                .updatedAt(s.getUpdatedAt())
                                .build();
        }

        private SoumissionDetailResponse toDetailResponse(Soumission s) {
                List<LigneOffreFinanciereResponse> lignes = ligneOffreFinanciereRepository.findBySoumissionId(s.getId())
                                .stream()
                                .map(l -> LigneOffreFinanciereResponse.builder()
                                                .id(l.getId())
                                                .designation(l.getDesignation())
                                                .quantite(l.getQuantite())
                                                .unite(l.getUnite())
                                                .prixUnitaire(l.getPrixUnitaire())
                                                .build())
                                .toList();

                return SoumissionDetailResponse.builder()
                                .id(s.getId())
                                .appelOffreId(s.getAppelOffreId())
                                .operateurId(s.getOperateurId())
                                .lotId(s.getLotId())
                                .reference(s.getReference())
                                .statut(s.getStatut())
                                .horodatageServeur(s.getHorodatageServeur())
                                .isElectronique(s.getIsElectronique())
                                .isDansDelai(s.getIsDansDelai())
                                .createdAt(s.getCreatedAt())
                                .updatedAt(s.getUpdatedAt())
                                .lignesOffreFinanciere(lignes)
                                // Sous-objets mappés si présents (lazy loading dans la transaction)
                                .build();
        }

        /**
         * Récupérer le résumé des anomalies IA pour toutes les soumissions d'un AO
         */
        public AnomaliesParAoResponse getAnomaliesParAo(String appelOffreId) {
                List<String> soumissionIds = soumissionRepository
                        .findByAppelOffreIdOrderByCreatedAtDesc(appelOffreId)
                        .stream()
                        .map(s -> s.getId())
                        .toList();

                if (soumissionIds.isEmpty()) {
                        return AnomaliesParAoResponse.builder()
                                .totalAnomalies(0)
                                .breakdown(Map.of())
                                .flaggedBids(List.of())
                                .build();
                }

                var anomalies = anomalieIaRepository.findBySoumissionIdIn(soumissionIds);

                Map<String, Long> breakdown = anomalies.stream()
                        .collect(Collectors.groupingBy(a -> a.getAnomalyType(), Collectors.counting()));

                List<AnomaliesParAoResponse.FlaggedBid> flaggedBids = anomalies.stream()
                        .map(a -> AnomaliesParAoResponse.FlaggedBid.builder()
                                .soumissionId(a.getSoumissionId())
                                .anomalyType(a.getAnomalyType())
                                .detail(a.getDetail())
                                .confidence(a.getConfidence())
                                .detectedAt(a.getDetectedAt())
                                .build())
                        .toList();

                return AnomaliesParAoResponse.builder()
                        .totalAnomalies(anomalies.size())
                        .breakdown(breakdown)
                        .flaggedBids(flaggedBids)
                        .build();
        }
}
