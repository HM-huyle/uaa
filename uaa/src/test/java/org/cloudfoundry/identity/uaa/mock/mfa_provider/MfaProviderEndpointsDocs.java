package org.cloudfoundry.identity.uaa.mock.mfa_provider;

import org.apache.commons.lang.ArrayUtils;
import org.cloudfoundry.identity.uaa.mfa.GoogleMfaProviderConfig;
import org.cloudfoundry.identity.uaa.mfa.JdbcMfaProviderProvisioning;
import org.cloudfoundry.identity.uaa.mfa.MfaProvider;
import org.cloudfoundry.identity.uaa.mock.InjectedMockContextTest;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneSwitchingFilter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.test.web.servlet.ResultActions;

import static org.cloudfoundry.identity.uaa.mfa.MfaProvider.MfaProviderType.GOOGLE_AUTHENTICATOR;
import static org.cloudfoundry.identity.uaa.test.SnippetUtils.fieldWithPath;
import static org.cloudfoundry.identity.uaa.test.SnippetUtils.parameterWithName;
import static org.cloudfoundry.identity.uaa.test.SnippetUtils.subFields;
import static org.cloudfoundry.identity.uaa.util.JsonUtils.serializeExcludingProperties;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.JsonFieldType.OBJECT;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MfaProviderEndpointsDocs extends InjectedMockContextTest {
    private static final String NAME_DESC = "Human-readable name for this provider. Must be alphanumeric.";
    private static final String ID_DESC = "Unique identifier for this provider. This is a GUID generated by UAA.";
    private static final String CREATED_DESC = "UAA sets the creation date.";
    private static final String LAST_MODIFIED_DESC = "UAA sets the last modification date.";
    private static final String IDENTITY_ZONE_ID_DESC = "Set to the zone that this provider will be active in. Determined either by the Host header or the zone switch header.";
    private static final FieldDescriptor NAME = fieldWithPath("name").required().type(STRING).description(NAME_DESC);
    private static final FieldDescriptor TYPE = fieldWithPath("type").required().type(STRING).description("Type of MFA provider. Available types include `google-authenticator`.");
    private static final FieldDescriptor LAST_MODIFIED = fieldWithPath("last_modified").description(LAST_MODIFIED_DESC);
    private static final FieldDescriptor ID = fieldWithPath("id").type(STRING).description(ID_DESC);
    private static final FieldDescriptor CREATED = fieldWithPath("created").description(CREATED_DESC);
    private static final FieldDescriptor IDENTITY_ZONE_ID = fieldWithPath("identityZoneId").type(STRING).description(IDENTITY_ZONE_ID_DESC);
    private static final HeaderDescriptor IDENTITY_ZONE_ID_HEADER = headerWithName(IdentityZoneSwitchingFilter.HEADER).optional().description("If using a `zones.<zoneId>.admin` scope/token, indicates what zone this request goes to by supplying a zone_id.");
    private static final HeaderDescriptor IDENTITY_ZONE_SUBDOMAIN_HEADER = headerWithName(IdentityZoneSwitchingFilter.SUBDOMAIN_HEADER).optional().description("If using a `zones.<zoneId>.admin` scope/token, indicates what zone this request goes to by supplying a subdomain.");
    private static final HeaderDescriptor MFA_AUTHORIZATION_HEADER = headerWithName("Authorization").description("Bearer token containing `uaa.admin` or `zones.<zoneId>.admin`");
    private FieldDescriptor[] commonProviderFields = {
            NAME,
            TYPE,
    };
    private String adminToken;
    private JdbcMfaProviderProvisioning mfaProviderProvisioning;

    @Before
    public void setUp() throws Exception {
        adminToken = testClient.getClientCredentialsOAuthAccessToken(
                "admin",
                "adminsecret",
                "");

        mfaProviderProvisioning = getWebApplicationContext().getBean(JdbcMfaProviderProvisioning.class);
    }

    @Test
    public void testCreateGoogleMfaProvider() throws Exception {
        MfaProvider<GoogleMfaProviderConfig> mfaProvider = getGoogleMfaProvider();

        FieldDescriptor[] idempotentFields = getGoogleMfaProviderFields();
        Snippet requestFields = requestFields(idempotentFields);

        Snippet responseFields = responseFields(getMfaProviderResponseFields(idempotentFields));
        getMockMvc().perform(RestDocumentationRequestBuilders.post("/mfa-providers", mfaProvider.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(serializeExcludingProperties(mfaProvider, "id", "created", "last_modified", "identityZoneId")))
                .andExpect(status().isCreated())
                .andDo(document("{ClassName}/{methodName}",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                                MFA_AUTHORIZATION_HEADER,
                                IDENTITY_ZONE_ID_HEADER
                        ),
                        requestFields,
                        responseFields)
                );
    }

    private FieldDescriptor[] getGoogleMfaProviderFields() {
        return (FieldDescriptor[]) ArrayUtils.addAll(commonProviderFields, new FieldDescriptor[]{
                fieldWithPath("config").optional(null).type(OBJECT).description("Human-readable provider description. Object with optional providerDescription and issue properties."),
                fieldWithPath("config.providerDescription").optional(null).type(STRING).description("Human-readable provider description. Only for backend description purposes."),
                fieldWithPath("config.issuer").optional(null).type(STRING).description("Human-readable tag for display purposes on MFA devices. Defaults to name of identity zone.")
        });
    }

    private FieldDescriptor[] getMfaProviderResponseFields(FieldDescriptor[] idempotentFields) {
        return (FieldDescriptor[]) ArrayUtils.addAll(idempotentFields, new FieldDescriptor[]{
                ID,
                CREATED,
                LAST_MODIFIED,
                IDENTITY_ZONE_ID
        });
    }

    private MfaProvider<GoogleMfaProviderConfig> getGoogleMfaProvider() {
        return (MfaProvider<GoogleMfaProviderConfig>) new MfaProvider<GoogleMfaProviderConfig>()
                    .setName("sampleGoogleMfaProvider"+new RandomValueStringGenerator(6).generate())
                    .setType(GOOGLE_AUTHENTICATOR)
                    .setConfig(new GoogleMfaProviderConfig().setProviderDescription("Google MFA for default zone"));
    }

    @Test
    public void testGetMfaProvider() throws Exception{
        MfaProvider<GoogleMfaProviderConfig> mfaProvider = getGoogleMfaProvider();
        mfaProvider = createMfaProviderHelper(mfaProvider);

        Snippet responseFields = responseFields(getMfaProviderResponseFields(getGoogleMfaProviderFields()));

        ResultActions getMFaResultAction = getMockMvc().perform(
                RestDocumentationRequestBuilders.get("/mfa-providers/{id}", mfaProvider.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(APPLICATION_JSON));

        getMFaResultAction.andDo(document(
                "{ClassName}/{methodName}",
                preprocessResponse(prettyPrint()),
                pathParameters(parameterWithName("id").required().description(ID_DESC)),
                requestHeaders(
                        MFA_AUTHORIZATION_HEADER,
                        IDENTITY_ZONE_ID_HEADER
                ),
                responseFields
        ));
    }

    @Test
    public void testListMfaProviders() throws Exception{
        MfaProvider<GoogleMfaProviderConfig> mfaProvider = getGoogleMfaProvider();
        createMfaProviderHelper(mfaProvider);

        Snippet responseFields = responseFields((FieldDescriptor[])
                subFields("[]", getMfaProviderResponseFields(getGoogleMfaProviderFields())));

        ResultActions listMfaProviderAction = getMockMvc().perform(RestDocumentationRequestBuilders.get("/mfa-providers")
                .header("Authorization", "Bearer " + adminToken)
                .accept(APPLICATION_JSON));

        listMfaProviderAction.andDo(
            document("{ClassName}/{methodName}",
            preprocessResponse(prettyPrint()),
            requestHeaders(
                    MFA_AUTHORIZATION_HEADER,
                    IDENTITY_ZONE_ID_HEADER),
            responseFields
        ));
    }

    @Test
    public void testDeleteMfaProvider() throws Exception {
        MfaProvider<GoogleMfaProviderConfig> mfaProvider = getGoogleMfaProvider();
        mfaProvider = createMfaProviderHelper(mfaProvider);

        Snippet responseFields = responseFields(getMfaProviderResponseFields(getGoogleMfaProviderFields()));

        ResultActions getMFaResultAction = getMockMvc().perform(
                RestDocumentationRequestBuilders.delete("/mfa-providers/{id}", mfaProvider.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(APPLICATION_JSON));

        getMFaResultAction.andDo(document(
                "{ClassName}/{methodName}",
                preprocessResponse(prettyPrint()),
                pathParameters(parameterWithName("id").required().description(ID_DESC)),
                requestHeaders(
                        MFA_AUTHORIZATION_HEADER,
                        IDENTITY_ZONE_ID_HEADER
                ),
                responseFields
        ));
    }

    private MfaProvider createMfaProviderHelper(MfaProvider<GoogleMfaProviderConfig> mfaProvider) throws Exception{
        MockHttpServletResponse createResponse = getMockMvc().perform(
                post("/mfa-providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(JsonUtils.writeValueAsString(mfaProvider))
                        .accept(APPLICATION_JSON)).andReturn().getResponse();
        Assert.assertEquals(HttpStatus.CREATED.value(), createResponse.getStatus());
        MfaProvider createdMfaProvider = JsonUtils.readValue(createResponse.getContentAsString(), MfaProvider.class);
        return createdMfaProvider;
    }

}