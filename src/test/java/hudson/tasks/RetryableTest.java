package hudson.tasks;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.*;

public class RetryableTest {

    /**
     * Retry 0x = do once
     */
    @Test
    public void testRetrySuccess() {
        final List<Integer> attempts = new ArrayList<>();
        final boolean isSuccess = Retryable.retry(0, (attempt) -> {
            attempts.add(attempt);
            // no-op
        });

        assertTrue(isSuccess);
        assertThat(attempts, contains(1));
    }

    @Test
    public void testRetryFailure() {
        final List<Integer> attempts = new ArrayList<>();
        final boolean isSuccess = Retryable.retry(0, (attempt) -> {
            attempts.add(attempt);
            throw new Exception("Fail");
        });

        assertFalse(isSuccess);
        assertThat(attempts, contains(1));
    }

    /**
     * Retry 2x = do three times
     */
    @Test
    public void testMultipleRetries() {
        final List<Integer> attempts = new ArrayList<>();

        final boolean isSuccess = Retryable.retry(2, (attempt) -> {
            attempts.add(attempt);
            throw new Exception("Fail");
        });

        assertFalse(isSuccess);
        assertThat(attempts, contains(1, 2, 3));
    }
}
