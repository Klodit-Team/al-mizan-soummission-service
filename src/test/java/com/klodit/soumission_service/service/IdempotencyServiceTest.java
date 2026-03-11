package com.klodit.soumission_service.service;

import com.klodit.soumission_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService — Tests")
class IdempotencyServiceTest {

    @InjectMocks
    private IdempotencyService idempotencyService;
    @Mock
    private ProcessedEventRepository repository;

    @Test
    @DisplayName("Message déjà traité → retourne true")
    void isAlreadyProcessed_true() {
        when(repository.existsByEventId("event-001")).thenReturn(true);
        assertThat(idempotencyService.isAlreadyProcessed("event-001")).isTrue();
    }

    @Test
    @DisplayName("Message nouveau → retourne false")
    void isAlreadyProcessed_false() {
        when(repository.existsByEventId("event-002")).thenReturn(false);
        assertThat(idempotencyService.isAlreadyProcessed("event-002")).isFalse();
    }

    @Test
    @DisplayName("markAsProcessed → sauvegarde en base")
    void markAsProcessed_saves() {
        idempotencyService.markAsProcessed("event-003", "test.type", "test.queue");
        verify(repository).save(argThat(e -> "event-003".equals(e.getEventId())
                && "test.type".equals(e.getEventType())
                && "test.queue".equals(e.getSourceQueue())));
    }
}
