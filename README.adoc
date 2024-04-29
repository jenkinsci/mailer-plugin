Mailer plugin for Jenkins
=========================

image:https://img.shields.io/jenkins/plugin/v/mailer.svg[link="https://plugins.jenkins.io/mailer"]
image:https://img.shields.io/github/release/jenkinsci/mailer-plugin.svg?label=changelog[link="https://github.com/jenkinsci/mailer-plugin/releases/latest"]
image:https://img.shields.io/jenkins/plugin/i/mailer.svg?color=blue[link="https://plugins.jenkins.io/mailer"]


This plugin allows you to configure email notifications for build results. This is a break-out of the original core based email component.

== Configuration

In order to be able to send E-Mail notifications mail server configuration must be introduced in the _Manage Jenkins_ page,  
_Configure System > E-mail Notification_ section. Available options are:

* **SMTP server**: Name of the mail server. If empty the system will try to use the default server 
(which is normally the one running on `localhost`). 
Jenkins uses https://javaee.github.io/javamail/[JavaMail] for sending out e-mails, and JavaMail allows additional settings to be given as system properties to the container. 
See http://jenkins-ci.org/javamail-properties[this document] for possible values and effects.
* **Default user e-mail suffix**: If your users' e-mail addresses can be computed automatically by simply adding a suffix, then specify that suffix if this field. 
Otherwise leave it empty. Note that users can always override the e-mail address selectively. 
For example, if this field is set to `@acme.org`, then user foo will by default get the e-mail address foo@acme.org.

There are some advanced options as well:

* **Use SMTP Authentication**: check this option to use SMTP authentication when sending out e-mails. 
If your environment requires the use of SMTP authentication, specify the user name and the password in the fields shown when this option is checked.
* **Use SSL**: Whether or not to use SSL for connecting to the SMTP server. 
Defaults to port `465`. 
Other advanced configurations can be done by setting system properties. See this document for possible values and effects.
* **Use TLS**: Whether or not to use TLS for connecting to the SMTP server.
Defaults to port `587`.
Other advanced configurations can be done by setting system properties. See this document for possible values and effects.
* **SMTP Port**: Port number for the mail server. 
Leave it empty to use the default port for the protocol (`587` if using TLS, `465` if using SSL, `25` if not using encryption).
* **Reply-To Address**: Address to include in the `Reply-To` header.
Up to version `1.16`, only one address is allowed, starting in version `1.17` more than one can be used.
* **Charset**: character set to use to construct the message.

In order to test the configuration, you can check the _Test configuration by sending test e-mail_ checkbox, provide a destination address at the _Test e-mail recipient_ field and clicking the _Test configuration_ button.

== Usage

E-Mail notifications are configured in jobs by adding an _E-mail notification_ Post-build Action. 
If configured, Jenkins will send out an e-mail to the specified recipients when a certain important event occurs:

* Every failed build triggers a new e-mail.
* A successful build after a failed (or unstable) build triggers a new e-mail, indicating that a crisis is over.
* An unstable build after a successful build triggers a new e-mail, indicating that there's a regression.
* Unless configured, every unstable build triggers a new e-mail, indicating that regression is still there.

The Recipients field must contain a whitespace or comma-separated list of recipient addresses. 
May reference build parameters like `$PARAM`.

Additional options include:

* **Send e-mail for every unstable build**: 
if checked, notifications will be sent for every unstable build and not only the first build after a successful one.
* **Send separate e-mails to individuals who broke the build**: 
if checked, the notification e-mail will be sent to individuals who have committed changes for the broken build (by assuming that those changes broke the build).
If e-mail addresses are also specified in the recipient list, then both the individuals as well as the specified addresses get the notification e-mail. 
If the recipient list is empty, then only the individuals will receive e-mails.

== Changelog

* For recent versions, see https://github.com/jenkinsci/mailer-plugin/releases[GitHub Releases]
* For versions 1.23 and older, see the https://wiki.jenkins.io/display/JENKINS/Mailer[Wiki page]
