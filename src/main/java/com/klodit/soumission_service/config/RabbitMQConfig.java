package com.klodit.soumission_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchange ───────────────────────────────────
    public static final String SOUMISSION_EXCHANGE = "soumission.exchange";

    // ── Routing Keys (publiés par ce service) ──────
    public static final String RK_SOUMISSION_DEPOSEE = "soumission.deposee";
    public static final String RK_SOUMISSION_RECUE = "soumission.recue";
    public static final String RK_SOUMISSION_ANALYSE = "soumission.analyse.demandee";
    public static final String RK_OFFRES_DECHIFFREES = "offres.dechiffrees";
    public static final String RK_OFFRE_FINANCIERE_ANALYSE = "offre.financiere.analyse.demandee";
    public static final String RK_AUDIT_DEPOT = "audit.depot.tentative";

    // ── Queues (consommées par ce service) ──────────
    public static final String QUEUE_AO_PUBLIE = "soumission.appel-offre-publie";
    public static final String QUEUE_AO_CLOTURE = "soumission.appel-offre-cloture";
    public static final String QUEUE_COMMISSION_OUVERTURE = "soumission.commission-ouverture";
    public static final String QUEUE_IA_ANALYSE_TERMINEE = "soumission.ia-analyse-terminee";
    public static final String QUEUE_OFFRE_FINANCIERE_ANALYSE_TERMINEE = "soumission.offre-financiere-analyse-terminee";

    // ── Queue sortante (déclarée ici pour visibilité dans la démo) ──
    public static final String QUEUE_ANALYSE_DEMANDEE = "soumission.analyse.demandee";
    public static final String QUEUE_OFFRE_FINANCIERE_ANALYSE_DEMANDEE = "offre.financiere.analyse.demandee";

    @Bean
    public TopicExchange soumissionExchange() {
        return new TopicExchange(SOUMISSION_EXCHANGE, true, false);
    }

    // ── Queues ─────────────────────────────────────
    @Bean
    public Queue queueAoPublie() {
        return QueueBuilder.durable(QUEUE_AO_PUBLIE).build();
    }

    @Bean
    public Queue queueAoCloture() {
        return QueueBuilder.durable(QUEUE_AO_CLOTURE).build();
    }

    @Bean
    public Queue queueCommissionOuverture() {
        return QueueBuilder.durable(QUEUE_COMMISSION_OUVERTURE).build();
    }

    @Bean
    public Queue queueIaAnalyseTerminee() {
        return QueueBuilder.durable(QUEUE_IA_ANALYSE_TERMINEE).build();
    }

    @Bean
    public Queue queueAnalyseDemandee() {
        return QueueBuilder.durable(QUEUE_ANALYSE_DEMANDEE).build();
    }

    @Bean
    public Queue queueOffreFinanciereAnalyseTerminee() {
        return QueueBuilder.durable(QUEUE_OFFRE_FINANCIERE_ANALYSE_TERMINEE).build();
    }

    @Bean
    public Queue queueOffreFinanciereAnalyseDemandee() {
        return QueueBuilder.durable(QUEUE_OFFRE_FINANCIERE_ANALYSE_DEMANDEE).build();
    }

    // ── Bindings ───────────────────────────────────
    @Bean
    public Binding bindingAoPublie() {
        return BindingBuilder.bind(queueAoPublie())
                .to(soumissionExchange())
                .with("appel_offre.publie");
    }

    @Bean
    public Binding bindingAoCloture() {
        return BindingBuilder.bind(queueAoCloture())
                .to(soumissionExchange())
                .with("appel_offre.cloture");
    }

    @Bean
    public Binding bindingCommissionOuverture() {
        return BindingBuilder.bind(queueCommissionOuverture())
                .to(soumissionExchange())
                .with("commission.ouverture.demandee");
    }

    @Bean
    public Binding bindingIaAnalyseTerminee() {
        return BindingBuilder.bind(queueIaAnalyseTerminee())
                .to(soumissionExchange())
                .with("ia.analyse.terminee");
    }

    @Bean
    public Binding bindingAnalyseDemandee() {
        return BindingBuilder.bind(queueAnalyseDemandee())
                .to(soumissionExchange())
                .with(RK_SOUMISSION_ANALYSE);
    }

    @Bean
    public Binding bindingOffreFinanciereAnalyseTerminee() {
        return BindingBuilder.bind(queueOffreFinanciereAnalyseTerminee())
                .to(soumissionExchange())
                .with("offre.financiere.analyse.terminee");
    }

    @Bean
    public Binding bindingOffreFinanciereAnalyseDemandee() {
        return BindingBuilder.bind(queueOffreFinanciereAnalyseDemandee())
                .to(soumissionExchange())
                .with(RK_OFFRE_FINANCIERE_ANALYSE);
    }

    // ── Sérialisation JSON ─────────────────────────
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setExchange(SOUMISSION_EXCHANGE);
        return template;
    }
}
