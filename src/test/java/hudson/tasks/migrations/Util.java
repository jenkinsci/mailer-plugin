package hudson.tasks.migrations;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;

import java.util.List;
import java.util.Optional;

public class Util {
    static Optional<StandardUsernamePasswordCredentials> lookupCredential(String id) {
        final List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (Item) null, null, (List<DomainRequirement>) null);

        return credentials.stream()
                .filter(cred -> cred.getId().equals(id))
                .findFirst();
    }
}
