package hudson.tasks;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

/**
 * Allow JavaMail to authenticate using a Jenkins StandardUsernamePasswordCredentials.
 */
class StandardUsernamePasswordCredentialsAuthenticator extends Authenticator {

    private final StandardUsernamePasswordCredentials credentials;

    StandardUsernamePasswordCredentialsAuthenticator(StandardUsernamePasswordCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(credentials.getUsername(), Secret.toString(credentials.getPassword()));
    }
}
