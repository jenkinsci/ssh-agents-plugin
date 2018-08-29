package hudson.plugins.sshslaves;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import com.trilead.ssh2.Connection;
import org.apache.commons.lang.StringUtils;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;

/**
 * Utility methods related with credentials management.
 */
public class SSHCredentialsManager {

    /**
     * The scheme requirement.
     */
    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");

    private static final Logger LOGGER = Logger.getLogger(SSHCredentialsManager.class.getName());

    /**
     * Look for a credential by Id.
     * @param credentialsId Id of the credential.
     * @return The StandardUsernameCredentials that matches to the ID
     */
    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId) {
        if(credentialsId == null){
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                                           SSH_SCHEME),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    /**
     * Look for a credential by Id and domain.
     * @param credentialsId Id to look for.
     * @param host Host of the domain to look for.
     * @param port port of the domain to look for.
     * @return The credential that matches Id and domain.
     */
    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId, String host, int port) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                                           SSH_SCHEME, new HostnamePortRequirement(host, port)),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    /**
     *
     * @param context context where looking for.
     * @param id Id of the credential.
     * @return Ok if the credential exists, otherwise an error.
     */
    public static FormValidation checkCredentialId(ItemGroup context, String id){
        if (!hasPermissionsToGetCredentials(context)) {
            return FormValidation.ok(); // no need to alarm a user that cannot configure
        }
        List<DomainRequirement> domainRequirements = Collections.singletonList(SSH_SCHEME);
        return checkCredentialId(context, id, domainRequirements);
    }

    /**
     * Look for a credential in a context in the domain for host + port, with the Id passed as parameter.
     * @param context context where looking for.
     * @param host host to compose the domain.
     * @param port post to compose the domain.
     * @param id Id of the credential.
     * @return Ok if the credential exists, otherwise an error.
     */
    public static FormValidation checkCredentialsIdAndDomain(ItemGroup context, String host, int port, String id) {
        if (!hasPermissionsToGetCredentials(context)) {
            return FormValidation.ok(); // no need to alarm a user that cannot configure
        }
        List<DomainRequirement> domainRequirements = Collections.singletonList(new HostnamePortRequirement(host, port));
        return checkCredentialId(context, id, domainRequirements);
    }

    /**
     * list all credentials in a context that match SSHAuthenticator.matcher(Connection.class)
     * @param context context where looking for.
     * @param domainRequirements domains where looking for.
     * @return a list of credentials.
     */
    private static ListBoxModel listCredentials(ItemGroup context, List<DomainRequirement> domainRequirements){
        return CredentialsProvider.listCredentials(StandardUsernameCredentials.class,
                                                   context,
                                                   ACL.SYSTEM,
                                                   domainRequirements,
                                                   SSHAuthenticator.matcher(Connection.class));
    }

    /**
     * Look for a credential in a context in domains with the Id passed as parameter.
     * @param context context where looking for.
     * @param id Id of the credential.
     * @param domainRequirements domains where looking for.
     * @return Ok if the credential exists, otherwise an error.
     */
    private static FormValidation checkCredentialId(ItemGroup context, String id,
                                                    List<DomainRequirement> domainRequirements) {
        for (ListBoxModel.Option o : listCredentials(context, domainRequirements)) {
            if (StringUtils.equals(id, o.value)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
    }

    /**
     *
     * @param context context where looking for.
     * @param credentialsId Id of the credential selected.
     * @return A ListBoxModel with the credentials available to select in the context.
     */
    public static ListBoxModel fillCredentialsIdItems(ItemGroup context, String credentialsId) {
        if (!hasPermissionsToGetCredentials(context)) {
            return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
        }
        List<DomainRequirement> domainRequirements = Collections.singletonList(SSH_SCHEME);
        return fillCredentialsIdItems(context, domainRequirements, credentialsId);
    }

    /**
     *
     * @param context context where looking for.
     * @param host host to compose the domain.
     * @param port port to compose the domain.
     * @param credentialsId Id of the credential selected.
     * @return A ListBoxModel with the credentials available to select in the context and domain.
     */
    public static ListBoxModel fillCredentialsIdItems(ItemGroup context, String host, int port, String credentialsId) {
        if (!hasPermissionsToGetCredentials(context)) {
            return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
        }
        List<DomainRequirement> domainRequirements = Collections.singletonList(new HostnamePortRequirement(host, port));
        return fillCredentialsIdItems(Jenkins.getInstance(), domainRequirements, credentialsId);
    }

    /**
     *
     * @param context context where looking for.
     * @param domainRequirements domain where looking for.
     * @param credentialsId Id of the credential selected.
     * @return A ListBoxModel with the credentials available to select in the context and domain.
     */
    private static ListBoxModel fillCredentialsIdItems(ItemGroup context, List<DomainRequirement> domainRequirements,
                                                String credentialsId) {
        return new StandardUsernameListBoxModel()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        context,
                        StandardUsernameCredentials.class,
                        domainRequirements,
                        SSHAuthenticator.matcher(Connection.class))
                .includeCurrentValue(credentialsId); // always add the current value last in case already present
    }

    /**
     * Check if the authenticated user has permissions to access to configure credentials.
     * @param context context to check.
     * @return true if the authenticated user has access to configure credentials in the context.
     */
    private static boolean hasPermissionsToGetCredentials(ItemGroup context) {
        AccessControlled _context = context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance();
        return _context != null && _context.hasPermission(Computer.CONFIGURE);
    }
}
