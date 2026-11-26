import jenkins.model.Jenkins
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def instance = Jenkins.get()
if (instance.getItem('online-feedback-srivastava') == null) {
    def realm = new HudsonPrivateSecurityRealm(false)
    realm.createAccount('naman-srivastava', new File('/run/secrets/jenkins_password').text.trim())
    instance.setSecurityRealm(realm)
    def authorization = new FullControlOnceLoggedInAuthorizationStrategy()
    authorization.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(authorization)
    instance.setNumExecutors(1)
    def job = instance.createProject(WorkflowJob, 'online-feedback-srivastava')
    job.setDefinition(new CpsFlowDefinition(new File('/lab-source/Jenkinsfile').text, true))
    job.save()
    instance.save()
    job.scheduleBuild2(0)
}
