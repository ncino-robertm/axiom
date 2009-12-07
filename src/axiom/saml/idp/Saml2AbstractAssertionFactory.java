package axiom.saml.idp;

import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.common.SAMLObjectBuilder;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AttributeValue;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.Issuer;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.impl.XSStringBuilder;


/**
 * Subclasses extend this class to build SAML 2.0 Assertions. 
 * @author rbrainard
 *
 */
public abstract class Saml2AbstractAssertionFactory extends AbstractAssertionFactory {

	private static Logger logger = Logger.getLogger(Saml2AbstractAssertionFactory.class);
	
	private String recipient;
	private String authnContextClassRefValue;
	private String nameIdFormat;
	private String ssoStartPage;
	private String logoutURL;

	/**
	 * No-arg constructor. Should only be created from IdpConfiguration or subclasses.
	 */	
	protected Saml2AbstractAssertionFactory(){}	
	
	/**
	 * Template method for building assertions. Cannot be overridden,
	 * but the hooks in the template must be overridden.
	 */
	@Override
    public final Assertion buildAssertion() throws IllegalStateException{
		logger.debug("Building AuthnContextClassRef");
		AuthnContextClassRef authnContextClassRef = ((SAMLObjectBuilder<AuthnContextClassRef>) builderFactory.getBuilder(AuthnContextClassRef.DEFAULT_ELEMENT_NAME)).buildObject();
			authnContextClassRef.setAuthnContextClassRef(authnContextClassRefValue);
			
		logger.debug("Building AuthnContext");
		AuthnContext authnContext = ((SAMLObjectBuilder<AuthnContext>) builderFactory.getBuilder(AuthnContext.DEFAULT_ELEMENT_NAME)).buildObject();
			authnContext.setAuthnContextClassRef(authnContextClassRef);
		
		logger.debug("Building AuthnStatement");
		AuthnStatement authStatement = ((SAMLObjectBuilder<AuthnStatement>) builderFactory.getBuilder(AuthnStatement.DEFAULT_ELEMENT_NAME)).buildObject();
			authStatement.setAuthnContext(authnContext);
			authStatement.setAuthnInstant(new DateTime());

		logger.debug("Building Audience");
		Audience audience = ((SAMLObjectBuilder<Audience>) builderFactory.getBuilder(Audience.DEFAULT_ELEMENT_NAME)).buildObject();;
			audience.setAudienceURI(getAudience());

		logger.debug("Building AudienceRestriction");
		AudienceRestriction audienceRestriction = ((SAMLObjectBuilder<AudienceRestriction>) builderFactory.getBuilder(AudienceRestriction.DEFAULT_ELEMENT_NAME)).buildObject();
			audienceRestriction.getAudiences().add(audience);
		
		logger.debug("Building Conditions");
		Conditions conditions = ((SAMLObjectBuilder<Conditions>) builderFactory.getBuilder(Conditions.DEFAULT_ELEMENT_NAME)).buildObject();
			conditions.setNotBefore(new DateTime());
			conditions.setNotOnOrAfter(new DateTime().plusSeconds(getExpiresInSeconds()));
			conditions.getAudienceRestrictions().add(audienceRestriction);

		logger.debug("Building Issuer");
		Issuer issuer = ((SAMLObjectBuilder<Issuer>) builderFactory.getBuilder(Issuer.DEFAULT_ELEMENT_NAME)).buildObject();
			issuer.setValue(getIssuer());

		logger.debug("Building Assertion");
		Assertion assertion = ((SAMLObjectBuilder<Assertion>) builderFactory.getBuilder(Assertion.DEFAULT_ELEMENT_NAME)).buildObject();
			if(getIssuer() != null){
				assertion.setIssuer(issuer);
			} else {
				throw new IllegalStateException("Issuer must not be null");
			}
			assertion.setIssueInstant(new DateTime());
			assertion.setVersion(SAMLVersion.VERSION_20);
			assertion.setID(IdGenerator.generateId());
			assertion.getAuthnStatements().add(authStatement);
			assertion.setConditions(conditions);
			assertion.setSubject(buildSubject());
			
			AttributeStatement attributeStatement = buildAttributeStatement();
			if(attributeStatement != null){
				assertion.getAttributeStatements().add(attributeStatement);
			} else {
				//do nothing
			}
		
		logger.info("Returning complete assertion with Id: " + assertion.getID() + " for UserId: " + getUserId());
		return assertion;
	}
	
	/**
	 * Base Subject building method that takes nameId as an argument.
	 * Subclasses call this method from the overridden no-arg buildSubject
	 * and provide their specialized nameId.
	 * @param nameIdentifierValue
	 */
	protected Subject buildSubject(String nameIdentifierValue) throws IllegalStateException{
		logger.debug("Building NameID");
		NameID nameId = ((SAMLObjectBuilder<NameID>) builderFactory.getBuilder(NameID.DEFAULT_ELEMENT_NAME)).buildObject();
			if(nameIdentifierValue != null){
				nameId.setValue(nameIdentifierValue);
				nameId.setFormat(nameIdFormat);
			} else {
				throw new IllegalStateException("nameIdentifierValue must not be null");
			}
			logger.debug("NameIdentifier set to " + nameIdentifierValue);
			
		logger.debug("Building SubjectConfirmationData");	
		SubjectConfirmationData subjectConfirmationData = ((SAMLObjectBuilder<SubjectConfirmationData>) builderFactory.getBuilder(SubjectConfirmationData.DEFAULT_ELEMENT_NAME)).buildObject();
			subjectConfirmationData.setNotOnOrAfter(new DateTime().plusSeconds(getExpiresInSeconds()));
			subjectConfirmationData.setRecipient(recipient);
		
		logger.debug("Building SubjectConfirmation");	
		SubjectConfirmation subjectConfirmation = ((SAMLObjectBuilder<SubjectConfirmation>) builderFactory.getBuilder(SubjectConfirmation.DEFAULT_ELEMENT_NAME)).buildObject();
			subjectConfirmation.setMethod(getSubjectConfirmationMethod());
			subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
		
		logger.debug("Building Subject");
		Subject subject = ((SAMLObjectBuilder<Subject>) builderFactory.getBuilder(Subject.DEFAULT_ELEMENT_NAME)).buildObject();
			subject.setNameID(nameId);
			subject.getSubjectConfirmations().add(subjectConfirmation);
		
		return subject;
	}

	/**
	 * Hook to be overridden by subclasses to provide Subject in the
	 * template, which then call back to buildSubject(String NameIdentifierValue)
	 * with their specific value for the name id
	 */
	protected abstract Subject buildSubject();
	
	
	protected AttributeStatement buildAttributeStatement(List<Attribute> additionalAttributes) throws IllegalStateException{				
		logger.debug("Building AttributeStatement");
		AttributeStatement attributeStatement = ((SAMLObjectBuilder<AttributeStatement>) builderFactory.getBuilder(AttributeStatement.DEFAULT_ELEMENT_NAME)).buildObject();
		
		if(ssoStartPage != null){
			XSString ssoStartPageAttributeValue = ((XSStringBuilder) Configuration.getBuilderFactory().getBuilder(XSString.TYPE_NAME)).buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
				ssoStartPageAttributeValue.setValue(ssoStartPage);

			Attribute ssoStartPageAttribute = ((SAMLObjectBuilder<Attribute>) builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME)).buildObject();
				ssoStartPageAttribute.setName("ssoStartPage");
				ssoStartPageAttribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified");					
				ssoStartPageAttribute.getAttributeValues().add(ssoStartPageAttributeValue);	
				
			attributeStatement.getAttributes().add(ssoStartPageAttribute);
		}

		if(logoutURL != null){
			XSString logoutUrlAttributeValue = ((XSStringBuilder) Configuration.getBuilderFactory().getBuilder(XSString.TYPE_NAME)).buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
				logoutUrlAttributeValue.setValue(logoutURL);

			Attribute logoutUrlAttribute = ((SAMLObjectBuilder<Attribute>) builderFactory.getBuilder(Attribute.DEFAULT_ELEMENT_NAME)).buildObject();
				logoutUrlAttribute.setName("logoutURL");
				logoutUrlAttribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified");					
				logoutUrlAttribute.getAttributeValues().add(logoutUrlAttributeValue);	
				
			attributeStatement.getAttributes().add(logoutUrlAttribute);
		}		
		
		if(additionalAttributes != null){
			attributeStatement.getAttributes().addAll(additionalAttributes);
		}
			
		logger.debug("Returning completed attributeStatement");
		return attributeStatement;
	}
	
	/**
	 * Hook to be overridden by subclasses to provide 
	 * AttributeStatement, if one needs to exist.
	 */	
	protected abstract AttributeStatement buildAttributeStatement();

	
	public String getRecipient() {
		return recipient;
	}

	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

	public String getAuthnContextClassRefValue() {
		return authnContextClassRefValue;
	}

	public void setAuthnContextClassRefValue(String authnContextClassRefValue) {
		this.authnContextClassRefValue = authnContextClassRefValue;
	}

	public String getNameIdFormat() {
		return nameIdFormat;
	}

	public void setNameIdFormat(String nameIdFormat) {
		this.nameIdFormat = nameIdFormat;
	}

	public String getSsoStartPage() {
		return ssoStartPage;
	}

	public void setSsoStartPage(String ssoStartPage) {
		this.ssoStartPage = ssoStartPage;
	}

	public String getLogoutURL() {
		return logoutURL;
	}

	public void setLogoutURL(String logoutURL) {
		this.logoutURL = logoutURL;
	}


	
}
