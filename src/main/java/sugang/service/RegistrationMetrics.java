package sugang.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class RegistrationMetrics {

    public static final String PHASE_COURSE_LOOKUP = "course_lookup";
    public static final String PHASE_DUPLICATE_CHECK = "duplicate_check";
    public static final String PHASE_APPLICATIONS_LOOKUP = "applications_lookup";
    public static final String PHASE_CONDITIONAL_UPDATE = "conditional_update";
    public static final String PHASE_APPLICATION_FLUSH = "application_flush";

    private static final String PHASE_METRIC = "sugang.registration.phase";
    private static final String TRANSACTION_METRIC = "sugang.registration.transaction";

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> phaseTimers;
    private final Timer committedTransactionTimer;
    private final Timer rolledBackTransactionTimer;
    private final Timer unknownTransactionTimer;

    public RegistrationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.phaseTimers = Map.of(
                PHASE_COURSE_LOOKUP, phaseTimer(meterRegistry, PHASE_COURSE_LOOKUP),
                PHASE_DUPLICATE_CHECK, phaseTimer(meterRegistry, PHASE_DUPLICATE_CHECK),
                PHASE_APPLICATIONS_LOOKUP, phaseTimer(meterRegistry, PHASE_APPLICATIONS_LOOKUP),
                PHASE_CONDITIONAL_UPDATE, phaseTimer(meterRegistry, PHASE_CONDITIONAL_UPDATE),
                PHASE_APPLICATION_FLUSH, phaseTimer(meterRegistry, PHASE_APPLICATION_FLUSH)
        );
        this.committedTransactionTimer = transactionTimer(meterRegistry, "committed");
        this.rolledBackTransactionTimer = transactionTimer(meterRegistry, "rolled_back");
        this.unknownTransactionTimer = transactionTimer(meterRegistry, "unknown");
    }

    public void observeTransactionCompletion() {
        Timer.Sample sample = Timer.start(meterRegistry);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sample.stop(unknownTransactionTimer);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                sample.stop(transactionTimerFor(status));
            }
        });
    }

    public <T> T recordPhase(String phase, Supplier<T> operation) {
        Timer timer = phaseTimers.get(phase);
        if (timer == null) {
            throw new IllegalArgumentException("알 수 없는 수강신청 측정 단계입니다: " + phase);
        }
        return timer.record(operation);
    }

    private Timer transactionTimerFor(int status) {
        return switch (status) {
            case TransactionSynchronization.STATUS_COMMITTED -> committedTransactionTimer;
            case TransactionSynchronization.STATUS_ROLLED_BACK -> rolledBackTransactionTimer;
            default -> unknownTransactionTimer;
        };
    }

    private static Timer phaseTimer(MeterRegistry meterRegistry, String phase) {
        return Timer.builder(PHASE_METRIC)
                .description("수강신청 트랜잭션 내부 단계별 실행 시간")
                .tag("phase", phase)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private static Timer transactionTimer(MeterRegistry meterRegistry, String outcome) {
        return Timer.builder(TRANSACTION_METRIC)
                .description("수강신청 트랜잭션 전체 실행 시간")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
