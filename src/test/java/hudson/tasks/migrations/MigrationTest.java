package hudson.tasks.migrations;

import hudson.tasks.Mailer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.net.URL;

/**
 * Defines migration tests for the Jenkins plugin configuration data model.
 * <p>The format is loosely based on Ruby On Rails' <a href="https://guides.rubyonrails.org/v5.1/active_record_migrations.html">ActiveRecord Migrations</a>. Like ActiveRecord Migrations, it uses naming conventions, and a single <code>change()</code> method per test case. Unlike ActiveRecord Migrations, there is no concept of migrating backwards as we do not support plugin downgrades, so we only need to test the forward path.</p>
 */
public abstract class MigrationTest {

    @Rule
    public final JenkinsRule jenkins = new JenkinsRule()
            .with(new LocalClass(this.getClass()));

    /**
     * Implement this to assert that the property in question has been migrated correctly.
     *
     * @param descriptor The descriptor in which the property will be found, or not found if it was removed.
     */
    protected abstract void change(Mailer.DescriptorImpl descriptor);

    @Test
    public void shouldMigrate() {
        final Mailer.DescriptorImpl config = getPluginConfiguration();
        change(config);
    }

    private Mailer.DescriptorImpl getPluginConfiguration() {
        return (Mailer.DescriptorImpl) jenkins.getInstance().getDescriptor(Mailer.class);
    }

    /**
     * Adaptation of Local for when we just want to load one Hudson home per class, rather than one per test.
     */
    private static class LocalClass implements HudsonHomeLoader {

        private final Class<?> testClass;

        public LocalClass(Class<?> testClass) {
            this.testClass = testClass;
        }

        @Override
        public File allocate() throws Exception {
            URL res = findDataResource();
            if(!res.getProtocol().equals("file"))
                throw new AssertionError("Test data is not available in the file system: "+res);
            File home = new File(res.toURI());
            System.err.println("Loading $JENKINS_HOME from " + home);

            return new CopyExisting(home).allocate();
        }

        private URL findDataResource() {
            for( String suffix : SUFFIXES ) {
                URL res = testClass.getResource(testClass.getSimpleName() + suffix);
                if(res!=null)   return res;
            }

            throw new AssertionError("No test resource was found for "+testClass);
        }

        private static final String[] SUFFIXES = {"/", ".zip"};
    }
}
