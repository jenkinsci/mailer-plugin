package hudson.tasks;

class Retryable {

    private Retryable() {

    }

    interface Fn {
        void run(int attempt) throws Exception;
    }

    /**
     * Run the provided function once, and if it fails, retry up to n times.
     *
     * This does not support asynchronous functions.
     *
     * @param times the number of times to retry after a failure. When times=0, the function runs once, with no retries if it fails. When times=2, the function runs once, with up to 2 retries if it fails.
     * @param fn the function to run and retry
     * @return whether any of the function invocations succeeded
     */
    static boolean retry(int times, Fn fn) {
        int attempt = 0;

        do {
            try {
                fn.run(attempt + 1);

                return true;
            } catch (Exception e) {
                // try again
            }

            ++attempt;
        } while (attempt <= times);

        return false;
    }
}
