/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Erik Ramfelt, Jean-Baptiste Quenot, Luca Domenico Milanesio
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.*;
import jenkins.plugins.mailer.tasks.i18n.Messages;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.XStream2;

import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends Notifier implements SimpleBuildStep {
    protected static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

    /**
     * Whitespace-separated list of e-mail addresses that represent recipients.
     */
    public String recipients;

    /**
     * If true, only the first unstable build will be reported.
     */
    public boolean dontNotifyEveryUnstableBuild;

    public boolean isNotifyEveryUnstableBuild() {
        return !dontNotifyEveryUnstableBuild;
    }

    /**
     * If true, individuals will receive e-mails regarding who broke the build.
     */
    public boolean sendToIndividuals;

    /**
     * Default Constructor.
     * 
     * This is left for backward compatibility.
     */
    @Deprecated
    public Mailer() {}

    /**
     * @param recipients one or more recipients with separators
     * @param notifyEveryUnstableBuild inverted for historical reasons.
     * @param sendToIndividuals if {@code true} mails are sent to individual committers
     */
    @DataBoundConstructor
    public Mailer(String recipients, boolean notifyEveryUnstableBuild, boolean sendToIndividuals) {
        this.recipients = recipients;
        this.dontNotifyEveryUnstableBuild = !notifyEveryUnstableBuild;
        this.sendToIndividuals = sendToIndividuals;
    }

    @Override
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "build cannnot be null and the workspace is not used in case it was null")
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        perform(build, build.getWorkspace(), launcher, listener);
        return true;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        if(debug)
            listener.getLogger().println("Running mailer");
        // substitute build parameters
        EnvVars env = build.getEnvironment(listener);
        String recip = env.expand(recipients);

        new MailSender(recip, dontNotifyEveryUnstableBuild, sendToIndividuals, descriptor().getCharset()) {
            /** Check whether a path (/-separated) will be archived. */
            @Override
            public boolean artifactMatches(String path, AbstractBuild<?,?> build) {
                // TODO a Notifier runs after a Recorder so it would make more sense to just check actual artifacts, not configuration
                // (Anyway currently this code would only be called for an AbstractBuild, since otherwise we cannot know what hyperlink to use for a random workspace.)
                ArtifactArchiver aa = build.getProject().getPublishersList().get(ArtifactArchiver.class);
                if (aa == null) {
                    LOGGER.finer("No ArtifactArchiver found");
                    return false;
                }
                String artifacts = aa.getArtifacts();
                for (String include : artifacts.split("[, ]+")) {
                    String pattern = include.replace(File.separatorChar, '/');
                    if (pattern.endsWith("/")) {
                        pattern += "**";
                    }
                    if (SelectorUtils.matchPath(pattern, path)) {
                        LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches true for {0} against {1}", new Object[] {path, pattern});
                        return true;
                    }
                }
                LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches for {0} matched none of {1}", new Object[] {path, artifacts});
                return false;
            }
        }.run(build,listener);
    }

    /**
     * This class does explicit check pointing.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private static Pattern ADDRESS_PATTERN = Pattern.compile("\\s*([^<]*)<([^>]+)>\\s*");
    
    /**
     * Deprecated! Converts a string to {@link InternetAddress}.
     * @param strAddress Address string
     * @param charset Charset (encoding) to be used
     * @return {@link InternetAddress} for the specified string
     * @throws AddressException Malformed address
     * @throws UnsupportedEncodingException Unsupported encoding
     *
     * @deprecated Use {@link #stringToAddress(java.lang.String, java.lang.String)}.
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("1.16")
    @SuppressFBWarnings(value = "NM_METHOD_NAMING_CONVENTION", justification = "It's deprecated and required for API compatibility")
    public static InternetAddress StringToAddress(String strAddress, String charset) throws AddressException, UnsupportedEncodingException {
        return stringToAddress(strAddress, charset);
    }
    
    /**
     * Converts a string to {@link InternetAddress}.
     * @param strAddress Address string
     * @param charset Charset (encoding) to be used
     * @return {@link InternetAddress} for the specified string
     * @throws AddressException Malformed address
     * @throws UnsupportedEncodingException Unsupported encoding
     * @since TODO
     */
    public static @NonNull InternetAddress stringToAddress(@NonNull String strAddress, 
            @NonNull String charset) throws AddressException, UnsupportedEncodingException {
        Matcher m = ADDRESS_PATTERN.matcher(strAddress);
        if(!m.matches()) {
            return new InternetAddress(strAddress);
        }

        String personal = m.group(1);
        String address = m.group(2);
        return new InternetAddress(address, personal, charset);
    }

    /**
     * @deprecated as of 1.286
     *      Use {@link #descriptor()} to obtain the current instance.
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("1.355")
    @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "Deprecated API field")
    public static DescriptorImpl DESCRIPTOR;

    public static DescriptorImpl descriptor() {
        return Jenkins.get().getDescriptorByType(Mailer.DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * The default e-mail address suffix appended to the user name found from changelog,
         * to send e-mails. Null if not configured.
         */
        private String defaultSuffix;

        /**
         * Hudson's own URL, to put into the e-mail.
         *
         * @deprecated as of 1.4
         *      Maintained in {@link JenkinsLocationConfiguration} but left here
         *      for compatibility just in case, so as not to lose this information.
         *      This is loaded to {@link JenkinsLocationConfiguration} via the XML file
         *      marshaled with {@link XStream2}.
         */
        private String hudsonUrl;

        /** @deprecated as of 1.23, use {@link #authentication} */
        @Deprecated
        private transient String smtpAuthUsername;

        
        /** @deprecated as of 1.23, use {@link #authentication} */
        @Deprecated
        private transient Secret smtpAuthPassword;

        private SMTPAuthentication authentication;

        /**
         * The e-mail address that Hudson puts to "From:" field in outgoing e-mails.
         * Null if not configured.
         *
         * @deprecated as of 1.4
         *      Maintained in {@link JenkinsLocationConfiguration} but left here
         *      for compatibility just in case, so as not to lose this information.
         */
        private String adminAddress;

        /**
         * The e-mail address that Jenkins puts to "Reply-To" header in outgoing e-mails.
         * Null if not configured.
         */
        private String replyToAddress;

        /**
         * The SMTP server to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>localhost</tt>.
         */
        private String smtpHost;
        
        /**
         * If true use SSL on port 465 (standard SMTPS) unless <code>smtpPort</code> is set.
         */
        private boolean useSsl;

        /**
         * If true use TLS on port 587 (standard STARTTLS) unless <code>smtpPort</code> is set.
         */
        private boolean useTls;

        /**
         * The SMTP port to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>25</tt>.
         */
        private String smtpPort;

        /**
         * The charset to use for the text and subject.
         */
        private String charset;
        
        /**
         * Used to keep track of number test e-mails.
         */
        private static transient AtomicInteger testEmailCount = new AtomicInteger(0);

        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", 
                justification = "Writing to a deprecated field")
        public DescriptorImpl() {
            load();
            DESCRIPTOR = this;
        }

        @NonNull
        @Override
        public Permission getRequiredGlobalConfigPagePermission() {
            return Jenkins.MANAGE;
        }

        public String getDisplayName() {
            return Messages.Mailer_DisplayName();
        }

        public String getDefaultSuffix() {
            return defaultSuffix;
        }

        public String getReplyToAddress() {
            return replyToAddress;
        }

        @DataBoundSetter
        public void setReplyToAddress(String address) {
            this.replyToAddress = Util.fixEmptyAndTrim(address);
            save();
        }

        /**
         * Creates a JavaMail session.
         * @return mail session based on the underlying session parameters.
         */
        public Session createSession() {
            return createSession(smtpHost,smtpPort,useSsl,useTls,getSmtpAuthUserName(),getSmtpAuthPasswordSecret());
        }
        private static Session createSession(String smtpHost, String smtpPort, boolean useSsl, boolean useTls, String smtpAuthUserName, Secret smtpAuthPassword) {
            final String SMTP_PORT_PROPERTY = "mail.smtp.port";
            final String SMTP_SOCKETFACTORY_PORT_PROPERTY = "mail.smtp.socketFactory.port";
            final String SMTP_SSL_ENABLE_PROPERTY = "mail.smtp.ssl.enable";

            smtpHost = Util.fixEmptyAndTrim(smtpHost);
            smtpPort = Util.fixEmptyAndTrim(smtpPort);
            smtpAuthUserName = Util.fixEmptyAndTrim(smtpAuthUserName);

            Properties props = new Properties(System.getProperties());
            if(smtpHost!=null) {
                props.put("mail.smtp.host", smtpHost);
            }
            if (smtpPort!=null) {
                props.put(SMTP_PORT_PROPERTY, smtpPort);
            }
            if (useSsl) {
            	/* This allows the user to override settings by setting system properties but
            	 * also allows us to use the default SMTPs port of 465 if no port is already set.
            	 * It would be cleaner to use smtps, but that's done by calling session.getTransport()...
            	 * and thats done in mail sender, and it would be a bit of a hack to get it all to
            	 * coordinate, and we can make it work through setting mail.smtp properties.
            	 */
            	if (props.getProperty(SMTP_SOCKETFACTORY_PORT_PROPERTY) == null) {
                    String port = smtpPort==null?"465":smtpPort;
                    props.put(SMTP_PORT_PROPERTY, port);
                    props.put(SMTP_SOCKETFACTORY_PORT_PROPERTY, port);
            	}
            	if (props.getProperty(SMTP_SSL_ENABLE_PROPERTY) == null) {
                    props.put(SMTP_SSL_ENABLE_PROPERTY, "true");
                    props.put("mail.smtp.ssl.checkserveridentity", true);
            	}
				props.put("mail.smtp.socketFactory.fallback", "false");
            	if (props.getProperty("mail.smtp.ssl.checkserveridentity") == null) {
                    props.put("mail.smtp.ssl.checkserveridentity", "true");
                }
			}
			if(useTls){
                /* This allows the user to override settings by setting system properties and
            	 * also allows us to use the default STARTTLS port, 587, if no port is already set.
            	 * Only the properties included below are required to use STARTTLS and they are
            	 * not expected to be enabled simultaneously with SSL (it will actually throw a
            	 * "javax.net.ssl.SSLException: Unrecognized SSL message, plaintext connection?"
            	 * if SMTP server expects only TLS).
            	 */
                if (props.getProperty(SMTP_SOCKETFACTORY_PORT_PROPERTY) == null) {
                    String port = smtpPort==null?"587":smtpPort;
                    props.put(SMTP_PORT_PROPERTY, port);
                    props.put(SMTP_SOCKETFACTORY_PORT_PROPERTY, port);
                }
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            if(smtpAuthUserName!=null)
                props.put("mail.smtp.auth","true");

            // avoid hang by setting some timeout. 
            props.put("mail.smtp.timeout","60000");
            props.put("mail.smtp.connectiontimeout","60000");

            return Session.getInstance(props,getAuthenticator(smtpAuthUserName,Secret.toString(smtpAuthPassword)));
        }

        private static Authenticator getAuthenticator(final String smtpAuthUserName, final String smtpAuthPassword) {
            if(smtpAuthUserName == null) {
            	return null;
            }
            return new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpAuthUserName,smtpAuthPassword);
                }
            };
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

            // Nested Describable (SMTPAuthentication) is not set to null in case it is not configured.
            // To mitigate that, it is being set to null before (so it gets set to sent value or null correctly) and, in
            // case of failure to databind, it gets reverted to previous value.
            // Would not be necessary by https://github.com/jenkinsci/jenkins/pull/3669
            SMTPAuthentication current = this.authentication;
            
            try (BulkChange b = new BulkChange(this)) {
                this.authentication = null;
                req.bindJSON(this, json);
                b.commit();
            } catch (IOException e) {
                this.authentication = current;
                throw new FormException("Failed to apply configuration", e, null);
            }
            
            return true;
        }

        private String nullify(String v) {
            if(v!=null && v.length()==0)    v=null;
            return v;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        
        /** @deprecated as of 1.23, use {@link #getSmtpHost()} */
        @Deprecated
        public String getSmtpServer() {
            return smtpHost;
        }

        /**
         * Method added to pass findbugs verification when compiling against 1.642.1
         * @return The JenkinsLocationConfiguration object.
         * @throws IllegalStateException if the object is not available (e.g., Jenkins not fully initialized).
         */
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification = "False positive. See https://sourceforge.net/p/findbugs/bugs/1411/")
        private JenkinsLocationConfiguration getJenkinsLocationConfiguration() {
            final JenkinsLocationConfiguration jlc = JenkinsLocationConfiguration.get();
            if (jlc == null) {
                throw new IllegalStateException("JenkinsLocationConfiguration not available");
            }
            return jlc;
        }

        /**
         * @deprecated as of 1.4
         *      Use {@link JenkinsLocationConfiguration} instead
         * @return administrator mail address
         */
        @Deprecated
        public String getAdminAddress() {
            return getJenkinsLocationConfiguration().getAdminAddress();
        }

        /**
         * @deprecated as of 1.4
         *      Use {@link JenkinsLocationConfiguration} instead
         * @return Jenkins base URL
         */
        @Deprecated
        public String getUrl() {
            return getJenkinsLocationConfiguration().getUrl();
        }

        /**
         * @deprecated as of 1.21
         *      Use {@link #authentication}
         */
        @Deprecated
        public String getSmtpAuthUserName() {
            if (authentication == null) return null;
            return authentication.getUsername();
        }

        /**
         * @deprecated as of 1.21
         *      Use {@link #authentication}
         */
        @Deprecated
        public String getSmtpAuthPassword() {
            if (authentication == null) return null;
            return Secret.toString(authentication.getPassword());
        }

        public Secret getSmtpAuthPasswordSecret() {
            if (authentication == null) return null;
            return authentication.getPassword();
        }

        public boolean getUseSsl() {
        	return useSsl;
        }

        public boolean getUseTls() {
            return useTls;
        }

        public String getSmtpPort() {
        	return smtpPort;
        }

        public String getCharset() {
        	String c = charset;
        	if (c == null || c.length() == 0)	c = "UTF-8";
        	return c;
        }

        @DataBoundSetter
        public void setDefaultSuffix(String defaultSuffix) {
            this.defaultSuffix = defaultSuffix;
            save();
        }

        /**
         * @deprecated as of 1.4
         *      Use {@link JenkinsLocationConfiguration} instead
         * @param hudsonUrl Jenkins base URL to set
         */
        @Deprecated
        public void setHudsonUrl(String hudsonUrl) {
            getJenkinsLocationConfiguration().setUrl(hudsonUrl);
        }

        /**
         * @deprecated as of 1.4
         *      Use {@link JenkinsLocationConfiguration} instead
         * @param adminAddress Jenkins administrator mail address to set
         */
        @Deprecated
        public void setAdminAddress(String adminAddress) {
            getJenkinsLocationConfiguration().setAdminAddress(adminAddress);
        }

        @DataBoundSetter
        public void setSmtpHost(String smtpHost) {
            this.smtpHost = Util.fixEmptyAndTrim(smtpHost);
            save();
        }

        @DataBoundSetter
        public void setUseSsl(boolean useSsl) {
            this.useSsl = useSsl;
            save();
        }

        @DataBoundSetter
        public void setUseTls(boolean useTls) {
            this.useTls = useTls;
            save();
        }

        @DataBoundSetter
        public void setSmtpPort(String smtpPort) {
            this.smtpPort = Util.fixEmptyAndTrim(smtpPort);
            save();
        }

        @DataBoundSetter
        public void setCharset(String charset) {
            if (charset == null || charset.length() == 0) {
                charset = "UTF-8";
            }
            this.charset = Util.fixEmptyAndTrim(charset);
            save();
        }

        @DataBoundSetter
        public void setAuthentication(@CheckForNull SMTPAuthentication authentication) {
            this.authentication = authentication;
            save();
        }

        @CheckForNull
        public SMTPAuthentication getAuthentication() {
            return authentication;
        }

        /**
         * @deprecated as of 1.21
         *      Use {@link #authentication}
         */
        @Deprecated
        public void setSmtpAuth(String userName, String password) {
            if (userName == null && password == null) {
                this.authentication = null;
            } else {
                this.authentication = new SMTPAuthentication(userName, Secret.fromString(password));
            }
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            Mailer m = (Mailer)super.newInstance(req, formData);

            if(hudsonUrl==null) {
                // if Hudson URL is not configured yet, infer some default
                hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }

            return m;
        }

        private Object readResolve() {
            if (smtpAuthPassword != null) {
                authentication = new SMTPAuthentication(smtpAuthUsername, smtpAuthPassword);
            }
            return this;
        }

        public FormValidation doAddressCheck(@QueryParameter String value) {
            try {
                new InternetAddress(value);
                return FormValidation.ok();
            } catch (AddressException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        @RequirePOST
        public FormValidation doCheckSmtpHost(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.MANAGE);
            try {
                if (Util.fixEmptyAndTrim(value)!=null)
                    InetAddress.getByName(value);
                return FormValidation.ok();
            } catch (UnknownHostException e) {
                return FormValidation.error(Messages.Mailer_Unknown_Host_Name()+value);
            }
        }

        public FormValidation doCheckDefaultSuffix(@QueryParameter String value) {
            if (value.matches("@[A-Za-z0-9.\\-]+") || Util.fixEmptyAndTrim(value)==null)
                return FormValidation.ok();
            else
                return FormValidation.error(Messages.Mailer_Suffix_Error());
        }

        /**
         * Send an email to the admin address
         * @throws IOException in case the active jenkins instance cannot be retrieved
         * @param smtpHost name of the SMTP server to use for mail sending
         * @param adminAddress Jenkins administrator mail address
         * @param authentication if set to {@code true} SMTP is used without authentication (username and password)
         * @param username plaintext username for SMTP authentication
         * @param password secret password for SMTP authentication
         * @param useSsl if set to {@code true} SSL is used
         * @param useTls if set to {@code true} TLS is used
         * @param smtpPort port to use for SMTP transfer
         * @param charset charset of the underlying MIME-mail message
         * @param sendTestMailTo mail address to send test mail to
         * @return response with http status code depending on the result of the mail sending
         */
        @RequirePOST
        public FormValidation doSendTestMail(
                @QueryParameter String smtpHost, @QueryParameter String adminAddress, @QueryParameter boolean authentication,
                @QueryParameter String username, @QueryParameter Secret password,
                @QueryParameter boolean useSsl, @QueryParameter boolean useTls, @QueryParameter String smtpPort, @QueryParameter String charset,
                @QueryParameter String sendTestMailTo) throws IOException {
            try {
                Jenkins.get().checkPermission(Jenkins.MANAGE);
                if (!authentication) {
                    username = null;
                    password = null;
                }
                
                MimeMessage msg = new MimeMessage(createSession(smtpHost, smtpPort, useSsl, useTls, username, password));
                msg.setSubject(Messages.Mailer_TestMail_Subject(testEmailCount.incrementAndGet()), charset);
                msg.setText(Messages.Mailer_TestMail_Content(testEmailCount.get(), Jenkins.get().getDisplayName()), charset);
                msg.setFrom(stringToAddress(adminAddress, charset));
                if (StringUtils.isNotBlank(replyToAddress)) {
                    msg.setReplyTo(new Address[]{stringToAddress(replyToAddress, charset)});
                }
                msg.setSentDate(new Date());
                msg.setRecipient(Message.RecipientType.TO, stringToAddress(sendTestMailTo, charset));

                Transport.send(msg);                
                return FormValidation.ok(Messages.Mailer_EmailSentSuccessfully());
            } catch (MessagingException e) {
                return FormValidation.errorWithMarkup("<p>"+Messages.Mailer_FailedToSendEmail()+"</p><pre>"+Util.escape(Functions.printThrowable(e))+"</pre>");
            }
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    /**
     * Per user property that is e-mail address.
     */
    public static class UserProperty extends hudson.model.UserProperty {
        /**
         * The user's e-mail address.
         * Null to leave it to default.
         */
        private final String emailAddress;

        @DataBoundConstructor
        public UserProperty(String emailAddress) {
            this.emailAddress = emailAddress;
        }

        @Exported
        public String getAddress() {
            if(hasExplicitlyConfiguredAddress()) {
                return emailAddress;
	        }

            // try the inference logic
            return MailAddressResolver.resolve(user);
        }

        public String getConfiguredAddress() {
            if(hasExplicitlyConfiguredAddress()) {
                return emailAddress;
            }

            // try the inference logic
            return MailAddressResolver.resolveFast(user);
        }

        @CheckForNull
        public String getEmailAddress() {
            return Util.fixEmptyAndTrim(emailAddress);
        }

        /**
         * Gets an email address, which have been explicitly configured on the
         * user's configuration page.
         * This method also truncates spaces. It is highly recommended to
         * use {@link #hasExplicitlyConfiguredAddress()} method to check the
         * option's existence.
         * @return A trimmed email address. It can be null
         * @since TODO
         */
        @CheckForNull
        public String getExplicitlyConfiguredAddress() {
            return Util.fixEmptyAndTrim(emailAddress);
        }

        /**
         * Has the user configured a value explicitly (true), or is it inferred (false)?
         * @return {@code true} if there is an email address available.
         */
        public boolean hasExplicitlyConfiguredAddress() {
            return Util.fixEmptyAndTrim(emailAddress)!=null;
        }

        @Extension
        @Symbol("mailer")
        public static final class DescriptorImpl extends UserPropertyDescriptor {
            public String getDisplayName() {
                return Messages.Mailer_UserProperty_DisplayName();
            }

            public UserProperty newInstance(User user) {
                return new UserProperty(null);
            }

            @Override
            public UserProperty newInstance(@CheckForNull StaplerRequest req, JSONObject formData) throws FormException {
                return new UserProperty(req != null ? req.getParameter("email.address") : null);
            }
        }
    }

    /**
     * Debug probe point to be activated by the scripting console.
     * @deprecated This hack may be removed in future versions
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", 
            justification = "It may used for debugging purposes. We have to keep it for the sake of the binary copatibility")
    @Deprecated
    public static boolean debug = false;
}
