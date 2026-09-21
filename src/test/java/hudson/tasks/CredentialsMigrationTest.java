package hudson.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.security.ACL;
import hudson.util.Secret;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests for the migration of legacy SMTP authentication to credentials
 */
@WithJenkins
class CredentialsMigrationTest {

    @Test
    @LocalData
    void testLegacySmtpMigrationToCredentials(JenkinsRule rule) {
        Mailer.DescriptorImpl d = Mailer.descriptor();

        assertNull(d.getAuthentication(), "Legacy authentication should've been cleared after migration");
        assertNotNull(d.getCredentialsId(), "credentialsId must be set after migration");
        StandardUsernamePasswordCredentials cred = findCredential(rule, d.getCredentialsId());
        assertNotNull(cred, "A credential matching credentialsId should exist in the store");
        assertEquals("olduser", cred.getUsername(), "Migrated username should match the old smtpAuthUsername");
    }

    @Test
    @LocalData
    void testLegacyAuthenticationMigrationToCredentials(JenkinsRule rule) {
        Mailer.DescriptorImpl d = Mailer.descriptor();

        assertNull(d.getAuthentication(), "authentication should've been null after migration");
        assertNotNull(d.getCredentialsId(), "credentialsId must be set after migration");
        StandardUsernamePasswordCredentials cred = findCredential(rule, d.getCredentialsId());
        assertNotNull(cred, "Credential must exist in store after migration");
        assertEquals("olduser", cred.getUsername(), "Username should match what was in the old authentication block");
    }

    @Test
    void testExistingCredentialIsReusedNotDuplicated(JenkinsRule rule) throws Exception {
        UsernamePasswordCredentialsImpl existing = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, null,
                "Already existing credential", "user", "pass"
        );
        addToStore(rule, existing);

        Mailer.DescriptorImpl d = Mailer.descriptor();
        d.setAuthentication(new SMTPAuthentication("user", Secret.fromString("pass")));
        d.setCredentialsId(null); // ensure credentialsid is unset to trigger migration path
        // Trigger migration
        d.migrateAuthenticationToCredentials();
        assertEquals(existing.getId(), d.getCredentialsId(), "Migration should be reusing the existing credential, not create a new one");
        long count = CredentialsProvider.lookupCredentialsInItemGroup(
                StandardUsernamePasswordCredentials.class,
                rule.jenkins, ACL.SYSTEM2, Collections.emptyList()
            ).stream()
        .filter(c -> "user".equals(c.getUsername()))
        .filter(c -> "pass".equals(Secret.toString(c.getPassword())))
        .count();
        assertEquals(1, count, "Should be exactly 1 credential with this username/password (no duplicate)");
    }

    @Test
    void testMigrationWithNullCredentialsDoesNotCreateStoreEntry(JenkinsRule rule) throws Exception {
        Mailer.DescriptorImpl d = Mailer.descriptor();

        int credsBefore = CredentialsProvider.lookupCredentialsInItemGroup(
                StandardUsernamePasswordCredentials.class,
                rule.jenkins, ACL.SYSTEM2, Collections.emptyList()
        ).size();

        d.setAuthentication(new SMTPAuthentication(null, Secret.fromString("")));
        d.setCredentialsId(null);
        d.migrateAuthenticationToCredentials();

        int credsAfter = CredentialsProvider.lookupCredentialsInItemGroup(
                StandardUsernamePasswordCredentials.class,
                rule.jenkins, ACL.SYSTEM2, Collections.emptyList()
        ).size();

        assertEquals(credsBefore, credsAfter, "Migration with null/empty credentials should not be stored");
    }

    @Test
    @LocalData
    void testMigratedCredentialHasCorrectScopeAndDescription(JenkinsRule rule) {
        Mailer.DescriptorImpl d = Mailer.descriptor();
        String credId = d.getCredentialsId();
        assertNotNull(credId, "credentialsId should've been set after migration");
        StandardUsernamePasswordCredentials cred = findCredential(rule, credId);
        assertNotNull(cred, "Migrated credential must exist");
        assertEquals(CredentialsScope.GLOBAL, cred.getScope(), "Migrated credential should have GLOBAL scope");
        assertTrue(cred.getDescription().contains("Migrated"), "Migrated credential description should indicate it was auto migrated");
    }

    @Test
    @LocalData
    void testCreateSessionUsesCredentialsAfterMigration(JenkinsRule rule) {
        Mailer.DescriptorImpl d = Mailer.descriptor();
        assertNull(d.getAuthentication(), "Legacy auth should've been cleared");
        assertNotNull(d.getCredentialsId(), "credentialsId must be set");
        // createSession should not throw, it should find the credential and use it
        assertDoesNotThrow(d::createSession, "createSession() should work correctly after migration to credentials");
    }

    private static StandardUsernamePasswordCredentials findCredential(JenkinsRule rule, String id) {
        return CredentialsProvider.lookupCredentialsInItemGroup(
                StandardUsernamePasswordCredentials.class,
                rule.jenkins,
                ACL.SYSTEM2,
                Collections.emptyList()
        ).stream()
         .filter(c -> c.getId().equals(id))
         .findFirst()
         .orElse(null);
    }

    private static void addToStore(JenkinsRule rule, UsernamePasswordCredentialsImpl credential) throws Exception {
        for (CredentialsStore store : CredentialsProvider.lookupStores(rule.jenkins)) {
            if (store.hasPermission(CredentialsProvider.CREATE)) {
                store.addCredentials(Domain.global(), credential);
                return;
            }
        }
        throw new AssertionError("No writable credentials store found");
    }
}
