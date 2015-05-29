/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Bruce Chapman, Daniel Dyer, Jean-Baptiste Quenot
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

import hudson.FilePath;
import hudson.Util;
import hudson.Functions;
import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.tasks.i18n.Messages;
import jenkins.model.Jenkins;
import jenkins.plugins.mailer.tasks.MailAddressFilter;
import jenkins.plugins.mailer.tasks.MimeMessageBuilder;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.AddressException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;

/**
 * Core logic of sending out notification e-mail.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public class MailSender {
    /**
     * Whitespace-separated list of e-mail addresses that represent recipients.
     */
    private String recipients;
    
    private List<AbstractProject> includeUpstreamCommitters = new ArrayList<AbstractProject>();

    /**
     * If true, only the first unstable build will be reported.
     */
    private boolean dontNotifyEveryUnstableBuild;

    /**
     * If true, individuals will receive e-mails regarding who broke the build.
     */
    private boolean sendToIndividuals;
    
    /**
     * The charset to use for the text and subject.
     */
    private String charset;


    public MailSender(String recipients, boolean dontNotifyEveryUnstableBuild, boolean sendToIndividuals) {
    	this(recipients, dontNotifyEveryUnstableBuild, sendToIndividuals, "UTF-8");
    }

    public MailSender(String recipients, boolean dontNotifyEveryUnstableBuild, boolean sendToIndividuals, String charset) {
        this(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals,charset, Collections.<AbstractProject>emptyList());
    }
  
    public MailSender(String recipients, boolean dontNotifyEveryUnstableBuild, boolean sendToIndividuals, String charset, Collection<AbstractProject> includeUpstreamCommitters) {
        this.recipients = Util.fixNull(recipients);
        this.dontNotifyEveryUnstableBuild = dontNotifyEveryUnstableBuild;
        this.sendToIndividuals = sendToIndividuals;
        this.charset = charset;
        this.includeUpstreamCommitters.addAll(includeUpstreamCommitters);
    }

    @Deprecated
    public boolean execute(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException {
        run(build, listener);
        return true;
    }

    public final void run(Run<?,?> build, TaskListener listener) throws InterruptedException {
        try {
            MimeMessage mail = createMail(build, listener);
            if (mail != null) {
                // if the previous e-mail was sent for a success, this new e-mail
                // is not a follow up
                Run<?, ?> pb = build.getPreviousBuild();
                if(pb!=null && pb.getResult()==Result.SUCCESS) {
                    mail.removeHeader("In-Reply-To");
                    mail.removeHeader("References");
                }

                Address[] allRecipients = mail.getAllRecipients();
                if (allRecipients != null) {
                    StringBuilder buf = new StringBuilder("Sending e-mails to:");
                    for (Address a : allRecipients) {
                        if (a!=null) {
                            buf.append(' ').append(a);
                        }
                    }
                    listener.getLogger().println(buf);
                    Transport.send(mail);

                    build.addAction(new MailMessageIdAction(mail.getMessageID()));
                } else {
                    listener.getLogger().println(Messages.MailSender_ListEmpty());
                }
            }
        } catch (MessagingException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }
    }

    /**
     * To correctly compute the state change from the previous build to this build,
     * we need to ignore aborted builds.
     * See http://www.nabble.com/Losing-build-state-after-aborts--td24335949.html
     *
     * <p>
     * And since we are consulting the earlier result, if the previous build is still running, behave as if this were the first build.
     */
    private Result findPreviousBuildResult(Run<?,?> b) throws InterruptedException {
        do {
            b=b.getPreviousBuild();
            if (b == null || b.isBuilding()) {
                return null;
            }
        } while((b.getResult()==Result.ABORTED) || (b.getResult()==Result.NOT_BUILT));
        return b.getResult();
    }

    @Deprecated
    protected MimeMessage getMail(AbstractBuild<?, ?> build, BuildListener listener) throws MessagingException, UnsupportedEncodingException, InterruptedException {
        return createMail(build, listener);
    }

    protected @CheckForNull MimeMessage createMail(Run<?,?> build, TaskListener listener) throws MessagingException, UnsupportedEncodingException, InterruptedException {
        // In case getMail was overridden elsewhere. Cannot use Util.isOverridden since it only works on public methods.
        try {
            Method m = getClass().getDeclaredMethod("getMail", AbstractBuild.class, BuildListener.class);
            if (m.getDeclaringClass() != MailSender.class) { // so, overridden
                if (build instanceof AbstractBuild && listener instanceof BuildListener) {
                    return getMail((AbstractBuild) build, (BuildListener) listener);
                } else {
                    throw new AbstractMethodError("you must override createMail rather than getMail");
                }
            } // else using MailSender itself (or overridden in an intermediate superclass, too obscure to check)
        } catch (NoSuchMethodException x) {
            // non-deprecated subclass
        }
        if (build.getResult() == Result.FAILURE) {
            return createFailureMail(build, listener);
        }

        if (build.getResult() == Result.UNSTABLE) {
            if (!dontNotifyEveryUnstableBuild)
                return createUnstableMail(build, listener);
            Result prev = findPreviousBuildResult(build);
            if (prev == Result.SUCCESS || prev == null)
                return createUnstableMail(build, listener);
        }

        if (build.getResult() == Result.SUCCESS) {
            Result prev = findPreviousBuildResult(build);
            if (prev == Result.FAILURE)
                return createBackToNormalMail(build, Messages.MailSender_BackToNormal_Normal(), listener);
            if (prev == Result.UNSTABLE)
                return createBackToNormalMail(build, Messages.MailSender_BackToNormal_Stable(), listener);
        }

        return null;
    }

    private MimeMessage createBackToNormalMail(Run<?, ?> build, String subject, TaskListener listener) throws MessagingException, UnsupportedEncodingException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build, Messages.MailSender_BackToNormalMail_Subject(subject)),charset);
        StringBuilder buf = new StringBuilder();
        appendBuildUrl(build, buf);
        msg.setText(buf.toString(),charset);

        return msg;
    }

    private static ChangeLogSet<? extends ChangeLogSet.Entry> getChangeSet(Run<?,?> build) {
        if (build instanceof AbstractBuild) {
            return ((AbstractBuild<?,?>) build).getChangeSet();
        } else {
            // TODO JENKINS-24141 call getChangeSets in general
            return ChangeLogSet.createEmpty(build);
        }
    }

    private MimeMessage createUnstableMail(Run<?, ?> build, TaskListener listener) throws MessagingException, UnsupportedEncodingException {
        MimeMessage msg = createEmptyMail(build, listener);

        String subject = Messages.MailSender_UnstableMail_Subject();

        Run<?, ?> prev = build.getPreviousBuild();
        boolean still = false;
        if(prev!=null) {
            if(prev.getResult()==Result.SUCCESS)
                subject =Messages.MailSender_UnstableMail_ToUnStable_Subject();
            else if(prev.getResult()==Result.UNSTABLE) {
                subject = Messages.MailSender_UnstableMail_StillUnstable_Subject();
                still = true;
            }
        }

        msg.setSubject(getSubject(build, subject),charset);
        StringBuilder buf = new StringBuilder();
        // Link to project changes summary for "still unstable" if this or last build has changes
        if (still && !(getChangeSet(build).isEmptySet() && getChangeSet(prev).isEmptySet()))
            appendUrl(Util.encode(build.getParent().getUrl()) + "changes", buf);
        else
            appendBuildUrl(build, buf);
        msg.setText(buf.toString(), charset);

        return msg;
    }

    private void appendBuildUrl(Run<?, ?> build, StringBuilder buf) {
        appendUrl(Util.encode(build.getUrl())
                  + (getChangeSet(build).isEmptySet() ? "" : "changes"), buf);
    }

    private void appendUrl(String url, StringBuilder buf) {
        String baseUrl = Mailer.descriptor().getUrl();
        if (baseUrl != null)
            buf.append(Messages.MailSender_Link(baseUrl, url)).append("\n\n");
    }

    private MimeMessage createFailureMail(Run<?, ?> build, TaskListener listener) throws MessagingException, UnsupportedEncodingException, InterruptedException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build, Messages.MailSender_FailureMail_Subject()),charset);

        StringBuilder buf = new StringBuilder();
        appendBuildUrl(build, buf);

        boolean firstChange = true;
        for (ChangeLogSet.Entry entry : getChangeSet(build)) {
            if (firstChange) {
                firstChange = false;
                buf.append(Messages.MailSender_FailureMail_Changes()).append("\n\n");
            }
            buf.append('[');
            buf.append(entry.getAuthor().getFullName());
            buf.append("] ");
            String m = entry.getMsg();
            if (m!=null) {
                buf.append(m);
                if (!m.endsWith("\n")) {
                    buf.append('\n');
                }
            }
            buf.append('\n');
        }

        buf.append("------------------------------------------\n");

        try {
            // Restrict max log size to avoid sending enormous logs over email.
            // Interested users can always look at the log on the web server.
            List<String> lines = build.getLog(MAX_LOG_LINES);

            String workspaceUrl = null, artifactUrl = null;
            Pattern wsPattern = null;
            String baseUrl = Mailer.descriptor().getUrl();
            if (baseUrl != null) {
                // Hyperlink local file paths to the repository workspace or build artifacts.
                // Note that it is possible for a failure mail to refer to a file using a workspace
                // URL which has already been corrected in a subsequent build. To fix, archive.
                workspaceUrl = baseUrl + Util.encode(build.getParent().getUrl()) + "ws/";
                artifactUrl = baseUrl + Util.encode(build.getUrl()) + "artifact/";
                FilePath ws = build instanceof AbstractBuild ? ((AbstractBuild) build).getWorkspace() : null;
                // Match either file or URL patterns, i.e. either
                // c:\hudson\workdir\jobs\foo\workspace\src\Foo.java
                // file:/c:/hudson/workdir/jobs/foo/workspace/src/Foo.java
                // will be mapped to one of:
                // http://host/hudson/job/foo/ws/src/Foo.java
                // http://host/hudson/job/foo/123/artifact/src/Foo.java
                // Careful with path separator between $1 and $2:
                // workspaceDir will not normally end with one;
                // workspaceDir.toURI() will end with '/' if and only if workspaceDir.exists() at time of call
                wsPattern = ws == null ? null : Pattern.compile("(" +
                    Pattern.quote(ws.getRemote()) + "|" + Pattern.quote(ws.toURI().toString()) + ")[/\\\\]?([^:#\\s]*)");
            }
            for (String line : lines) {
                line = line.replace('\0',' '); // shall we replace other control code? This one is motivated by http://www.nabble.com/Problems-with-NULL-characters-in-generated-output-td25005177.html
                if (wsPattern != null) {
                    // Perl: $line =~ s{$rx}{$path = $2; $path =~ s!\\\\!/!g; $workspaceUrl . $path}eg;
                    Matcher m = wsPattern.matcher(line);
                    int pos = 0;
                    while (m.find(pos)) {
                        String path = m.group(2).replace(File.separatorChar, '/');
                        String linkUrl = artifactMatches(path, (AbstractBuild) build) ? artifactUrl : workspaceUrl;
                        String prefix = line.substring(0, m.start()) + '<' + linkUrl + Util.encode(path) + '>';
                        pos = prefix.length();
                        line = prefix + line.substring(m.end());
                        // XXX better style to reuse Matcher and fix offsets, but more work
                        m = wsPattern.matcher(line);
                    }
                }
                buf.append(line);
                buf.append('\n');
            }
        } catch (IOException e) {
            // somehow failed to read the contents of the log
            buf.append(Messages.MailSender_FailureMail_FailedToAccessBuildLog()).append("\n\n").append(Functions.printThrowable(e));
        }

        msg.setText(buf.toString(),charset);

        return msg;
    }

    private MimeMessage createEmptyMail(final Run<?, ?> build, final TaskListener listener) throws MessagingException, UnsupportedEncodingException {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder()
                .setCharset(charset)
                .setListener(listener);

        // TODO: I'd like to put the URL to the page in here,
        // but how do I obtain that?

        StringTokenizer tokens = new StringTokenizer(recipients);
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken();
            if (build instanceof AbstractBuild && address.startsWith("upstream-individuals:")) {
                // people who made a change in the upstream
                String projectName = address.substring("upstream-individuals:".length());
                AbstractProject up = Jenkins.getInstance().getItem(projectName, build.getParent(), AbstractProject.class);
                if(up==null) {
                    listener.getLogger().println("No such project exist: "+projectName);
                    continue;
                }
                messageBuilder.addRecipients(getCulpritsOfEmailList(up, (AbstractBuild) build, listener));
            } else {
                // ordinary address
                messageBuilder.addRecipients(address);
            }
        }

        for (AbstractProject project : includeUpstreamCommitters) {
            messageBuilder.addRecipients(getCulpritsOfEmailList(project, (AbstractBuild) build, listener));
        }

        if (sendToIndividuals && build instanceof AbstractBuild) {
            Set<User> culprits = ((AbstractBuild) build).getCulprits();

            if(debug)
                listener.getLogger().println("Trying to send e-mails to individuals who broke the build. sizeof(culprits)=="+culprits.size());

            messageBuilder.addRecipients(getUserEmailList(listener, culprits));
        }
        
        // set recipients after filtering out recipients that should not receive emails
        messageBuilder.setRecipientFilter(new MimeMessageBuilder.AddressFilter() {
            @Override
            public Set<InternetAddress> apply(Set<InternetAddress> recipients) {
                return MailAddressFilter.filterRecipients(build, listener, recipients);
            }
        });

        MimeMessage msg = messageBuilder.buildMimeMessage();

        msg.addHeader("X-Jenkins-Job", build.getParent().getFullName());
        msg.addHeader("X-Jenkins-Result", build.getResult().toString());

        Run<?, ?> pb = build.getPreviousBuild();
        if(pb!=null) {
            MailMessageIdAction b = pb.getAction(MailMessageIdAction.class);
            if(b!=null) {
                MimeMessageBuilder.setInReplyTo(msg, b.messageId);
            }
        }

        return msg;
    }

    String getCulpritsOfEmailList(AbstractProject upstreamProject, AbstractBuild<?, ?> currentBuild, TaskListener listener) throws AddressException, UnsupportedEncodingException {
        AbstractBuild<?,?> upstreamBuild = currentBuild.getUpstreamRelationshipBuild(upstreamProject);
        AbstractBuild<?,?> previousBuild = currentBuild.getPreviousBuild();
        AbstractBuild<?,?> previousBuildUpstreamBuild = previousBuild!=null ? previousBuild.getUpstreamRelationshipBuild(upstreamProject) : null;
        if(previousBuild==null && upstreamBuild==null && previousBuildUpstreamBuild==null) {
            listener.getLogger().println("Unable to compute the changesets in "+ upstreamProject +". Is the fingerprint configured?");
            return null;
        }
        if(previousBuild==null || upstreamBuild==null || previousBuildUpstreamBuild==null) {
            listener.getLogger().println("Unable to compute the changesets in "+ upstreamProject);
            return null;
        }
        AbstractBuild<?,?> b=previousBuildUpstreamBuild;

        StringBuilder culpritEmails = new StringBuilder();
        do {
            b = b.getNextBuild();
            if (b != null) {
                String userEmails = getUserEmailList(listener, b.getCulprits());
                if (userEmails != null) {
                    if (culpritEmails.length() > 0) {
                        culpritEmails.append(",");
                    }
                    culpritEmails.append(userEmails);
                }
            }
        } while ( b != upstreamBuild && b != null );

        return culpritEmails.toString();
    }

    private String getUserEmailList(TaskListener listener, Set<User> users) throws AddressException, UnsupportedEncodingException {
        StringBuilder userEmails = new StringBuilder();
        for (User a : users) {
            String adrs = Util.fixEmpty(a.getProperty(Mailer.UserProperty.class).getAddress());
            if(debug)
                listener.getLogger().println("  User "+a.getId()+" -> "+adrs);
            if (adrs != null) {
                if (userEmails.length() > 0) {
                    userEmails.append(",");
                }
                userEmails.append(adrs);
            } else if (!a.getId().isEmpty()){
                /* the user is a single-segment (domain-less) email address  */
                userEmails.append(a.getId());
            } else {
                listener.getLogger().println(Messages.MailSender_NoAddress(a.getFullName()));
            }
        }
        return userEmails.toString();
    }

    private String getSubject(Run<?, ?> build, String caption) {
        return caption + ' ' + build.getFullDisplayName();
    }

    /**
     * Check whether a path (/-separated) will be archived.
     */
    protected boolean artifactMatches(String path, AbstractBuild<?, ?> build) {
        return false;
    }

    public static boolean debug = false;

    private static final int MAX_LOG_LINES = Integer.getInteger(MailSender.class.getName()+".maxLogLines",250);

}
