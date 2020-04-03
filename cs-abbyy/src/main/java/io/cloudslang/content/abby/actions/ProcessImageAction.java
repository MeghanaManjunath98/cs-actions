package io.cloudslang.content.abby.actions;

import com.hp.oo.sdk.content.annotations.Action;
import com.hp.oo.sdk.content.annotations.Output;
import com.hp.oo.sdk.content.annotations.Param;
import com.hp.oo.sdk.content.annotations.Response;
import com.hp.oo.sdk.content.plugin.ActionMetadata.MatchType;
import com.hp.oo.sdk.content.plugin.ActionMetadata.ResponseType;
import io.cloudslang.content.abby.constants.InputNames;
import io.cloudslang.content.abby.constants.OutputNames;
import io.cloudslang.content.abby.entities.ProcessImageInput;
import io.cloudslang.content.abby.services.ProcessImageService;
import io.cloudslang.content.abby.utils.ResultUtils;
import io.cloudslang.content.constants.ResponseNames;
import io.cloudslang.content.constants.ReturnCodes;

import java.util.Map;


public class ProcessImageAction {

    /**
     * Converts a given image to text in the specified output format using the ABBYY Cloud OCR SDK.
     *
     * @param locationId               The ID of the processing location to be used. Please note that the connection of your
     *                                 application to the processing location is specified manually during application creation,
     *                                 i.e. the application is bound to work with only one of the available locations.
     *                                 Valid values: 'cloud-eu', 'cloud-westus'.
     * @param applicationId            The ID of the application to be used.
     * @param password                 The password for the application.
     * @param timeToWait               //TODO
     * @param numberOfRetries          //TODO
     * @param proxyHost                The proxy server used to access the web site.
     * @param proxyPort                The proxy server port. The value '-1' indicates that the proxy port is not set
     *                                 and the scheme default port will be used, e.g. if the scheme is 'http://' and
     *                                 the 'proxyPort' is set to '-1' then port '80' will be used.
     *                                 Valid values: -1 and integer values greater than 0.
     *                                 Default value: 8080.
     * @param proxyUsername            The user name used when connecting to the proxy.
     * @param proxyPassword            The proxy server password associated with the proxyUsername input value.
     * @param trustAllRoots            Specifies whether to enable weak security over SSL/TSL. A certificate is trusted
     *                                 even if no trusted certification authority issued it.
     *                                 Valid values: 'true', 'false'.
     *                                 Default value: 'false'.
     * @param x509HostnameVerifier     Specifies the way the server hostname must match a domain name in the subject's Common Name (CN)
     *                                 or subjectAltName field of the X.509 certificate. Set this to "allow_all" to skip any checking.
     *                                 For the value "browser_compatible" the hostname verifier works the same way as Curl and Firefox.
     *                                 The hostname must match either the first CN, or any of the subject-alts.
     *                                 A wildcard can occur in the CN, and in any of the subject-alts. The only difference
     *                                 between "browser_compatible" and "strict" is that a wildcard (such as "*.foo.com")
     *                                 with "browser_compatible" matches all subdomains, including "a.b.foo.com".
     *                                 Valid values: 'strict','browser_compatible','allow_all'.
     *                                 Default value: 'strict'.
     * @param trustKeystore            The pathname of the Java TrustStore file. This contains certificates from other parties
     *                                 that you expect to communicate with, or from Certificate Authorities that you trust to
     *                                 identify other parties.  If the protocol (specified by the 'url') is not 'https' or if
     *                                 trustAllRoots is 'true' this input is ignored.
     *                                 Default value: <OO_Home>/java/lib/security/cacerts. Format: Java KeyStore (JKS)
     * @param trustPassword            The password associated with the TrustStore file.
     *                                 If trustAllRoots is 'false' and trustKeystore is empty, trustPassword default will be supplied.
     * @param connectTimeout           The time to wait for a connection to be established, in seconds.
     *                                 A timeout value of '0' represents an infinite timeout.
     *                                 Default value: '0'.
     * @param socketTimeout            The timeout for waiting for data (a maximum period inactivity between two consecutive data packets),
     *                                 in seconds. A socketTimeout value of '0' represents an infinite timeout.
     *                                 Default value: '0'.
     * @param useCookies               Specifies whether to enable cookie tracking or not. Cookies are stored between consecutive calls
     *                                 in a serializable session object therefore they will be available on a branch level.
     *                                 Default value: 'true'.
     * @param keepAlive                Specifies whether to create a shared connection that will be used in subsequent calls.
     *                                 If keepAlive is 'false', the already open connection will be used and after execution it will close it.
     *                                 The operation will use a connection pool stored in a GlobalSessionObject that will be available throughout
     *                                 the execution (the flow and subflows, between parallel split lanes).
     *                                 Valid values: 'true', 'false'. Default value: 'true'.
     * @param connectionsMaxPerRoute   The maximum limit of connections on a per route basis.
     *                                 The default will create no more than 2 concurrent connections per given route.
     *                                 Default value: '2'.
     * @param connectionsMaxTotal      The maximum limit of connections in total.
     *                                 The default will create no more than 2 concurrent connections in total.
     *                                 Default value: '20'.
     * @param headers                  The list containing the headers to use for the request separated by new line (CRLF).
     *                                 The header name - value pair will be separated by ":". Format: According to HTTP standard for headers (RFC 2616).
     *                                 Examples: 'Accept:text/plain'
     * @param responseCharacterSet     The character encoding to be used for the HTTP response.
     *                                 If responseCharacterSet is empty, the charset from the 'Content-Type' HTTP response header will be used.
     *                                 If responseCharacterSet is empty and the charset from the HTTP response Content-Type header is empty,
     *                                 the default value will be used. You should not use this for method=HEAD or OPTIONS.
     *                                 Default value: 'ISO-8859-1'.
     * @param destinationFile          The absolute path of a file on disk where to save the entity returned by the response.
     *                                 'returnResult' will no longer be populated with the entity if this is specified.
     *                                 Example: 'C:\temp\destinationFile.txt'.
     * @param sourceFile               The absolute path of the image to be loaded and converted using the SDK.
     * @param chunkedRequestEntity     Data is sent in a series of "chunks". It uses the Transfer-Encoding HTTP header in place
     *                                 of the Content-Length header. Generally it is recommended to let HttpClient choose the
     *                                 most appropriate transfer encoding based on the properties of the HTTP message being transferred.
     *                                 It is possible, however, to inform HttpClient that chunk coding is preferred by setting this input to "true".
     *                                 Please note that HttpClient will use this flag as a hint only.
     *                                 This value will be ignored when using HTTP protocol versions that do not support chunk coding, such as HTTP/1.0.
     *                                 This setting is ignored for multipart post entities.
     * @param language                 Specifies recognition language of the document. This parameter can contain several language
     *                                 names separated with commas, for example "English,French,German".
     *                                 Valid: see the official ABBYY CLoud OCR SDK documentation.
     *                                 Default: 'English'.
     * @param profile                  Specifies a profile with predefined processing settings.
     *                                 Valid values: 'documentConversion', 'documentArchiving', 'textExtraction', 'barcodeRecognition'.
     *                                 Default: 'documentConversion'.
     * @param textType                 Specifies the type of the text on a page.
     *                                 This parameter may also contain several text types separated with commas, for example "normal,matrix".
     *                                 Valid values: 'normal', 'typewriter', 'matrix', 'index', 'ocrA', 'ocrB', 'e13b', 'cmc7', 'gothic'.
     *                                 Default: 'normal'.
     * @param imageSource              Specifies the source of the image. It can be either a scanned image, or a photograph created
     *                                 with a digital camera. Special preprocessing operations can be performed with the image depending
     *                                 on the selected source. For example, the system can automatically correct distorted text lines,
     *                                 poor focus and lighting on photos.
     *                                 Valid values: 'auto', 'photo', 'scanner'.
     *                                 Default: 'auto'.
     * @param correctOrientation       Specifies whether the orientation of the image should be automatically detected and corrected.
     *                                 Valid values: 'true', 'false'.
     *                                 Default: 'true'.
     * @param correctSkew              Specifies whether the skew of the image should be automatically detected and corrected.
     *                                 Valid values: 'true', 'false'.
     *                                 Default: 'true'.
     * @param readBarcodes             Specifies whether barcodes must be detected on the image, recognized and exported to the result file.
     *                                 Valid values: 'true', 'false'.
     *                                 Default: 'true' if 'exportFormat' input value is set to 'xml', 'false' otherwise.
     * @param exportFormat             Specifies the export format.
     *                                 This parameter can contain up to three export formats, separated with commas (example: "pdfa,txt,xml").
     *                                 Valid values: 'txt', 'txtUnstructured', 'rtf', 'docx', 'xlsx', 'pptx', 'pdfSearchable',
     *                                 'pdfTextAndImages', 'pdfa', 'xml', 'xmlForCorrectedImage', 'alto'.
     *                                 Default: 'xml'.
     * @param writeFormatting          Specifies whether the paragraph and character styles should be written to an output file
     *                                 in XML format. This parameter can be used only if the 'exportFormat' parameter contains 'xml'
     *                                 or 'xmlForCorrectedImage' value.
     *                                 Valid values: 'true', 'false'.
     *                                 Default: 'false'.
     * @param writeRecognitionVariants Specifies whether the variants of characters recognition should be written to an output file
     *                                 in XML format. This parameter can be used only if the 'exportFormat' parameter contains 'xml'
     *                                 or xmlForCorrectedImage value.
     *                                 Valid values: 'true', 'false'.
     *                                 Default: 'false'.
     * @param writeTags                Specifies whether the result must be written as tagged PDF. This parameter can be
     *                                 used only if the exportFormat parameter contains one of the values for export to PDF.
     *                                 Valid values: 'auto', 'write', 'dontWrite'.
     *                                 Default: 'auto'.
     * @param description              Contains the description of the processing task. Cannot contain more than 255 characters.
     * @param pdfPassword              Contains a password for accessing password-protected images in PDF format.
     * @return a map containing the output of the operations. Keys present in the map are:
     * <br><b>returnResult</b> - Contains the text returned in the response body, if the output source was TXT,
     *                           otherwise if will contain a human readable message mentioning the success or failure of the task.
     * <br><b>taskId</b> - The ID of the task registered in the ABBYY server.
     * <br><b>credits</b> - The amount of ABBYY credits spent on the action.
     * <br><b>resultUrl</b> - The URL at which the result of the recognition process can be found.
     * <br><b>statusCode</b> - The status_code returned by the server.
     * <br><b>returnCode</b> - The returnCode of the operation: 0 for success, -1 for failure.
     * <br><b>exception</b> - The exception message if the operation goes to failure.
     * <br><b>failureMessage</b> //TODO
     * <br><b>timedOut</b> //TODO
     * <br><b>responseHeaders</b> //TODO
     */
    @Action(name = "Process Image",
            outputs = {
                    @Output(io.cloudslang.content.constants.OutputNames.RETURN_RESULT),
                    @Output(OutputNames.TASK_ID),
                    @Output(OutputNames.CREDITS),
                    @Output(OutputNames.RESULT_URL),
                    @Output(OutputNames.STATUS_CODE),
                    @Output(io.cloudslang.content.constants.OutputNames.RETURN_CODE),
                    @Output(io.cloudslang.content.constants.OutputNames.EXCEPTION),
                    @Output(OutputNames.FAILURE_MESSAGE),
                    @Output(OutputNames.TIMED_OUT),
                    @Output(OutputNames.RESPONSE_HEADERS)
            },
            responses = {
                    @Response(text = ResponseNames.SUCCESS, field = io.cloudslang.content.constants.OutputNames.RETURN_CODE,
                            value = ReturnCodes.SUCCESS,
                            matchType = MatchType.COMPARE_EQUAL, responseType = ResponseType.RESOLVED),
                    @Response(text = ResponseNames.FAILURE, field = io.cloudslang.content.constants.OutputNames.RETURN_CODE,
                            value = ReturnCodes.FAILURE,
                            matchType = MatchType.COMPARE_EQUAL, responseType = ResponseType.ERROR)
            })
    public Map<String, String> execute(
            @Param(value = InputNames.LOCATION_ID, required = true) String locationId,
            @Param(value = InputNames.APPLICATION_ID, required = true) String applicationId,
            @Param(value = InputNames.PASSWORD, required = true, encrypted = true) String password,
            @Param(value = InputNames.TIME_TO_WAIT) String timeToWait,
            @Param(value = InputNames.NUMBER_OF_RETRIES) String numberOfRetries,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.PROXY_HOST) String proxyHost,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.PROXY_PORT) String proxyPort,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.PROXY_USERNAME) String proxyUsername,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.PROXY_PASSWORD, encrypted = true) String proxyPassword,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.TRUST_ALL_ROOTS) String trustAllRoots,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.X509_HOSTNAME_VERIFIER) String x509HostnameVerifier,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.TRUST_KEYSTORE) String trustKeystore,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.TRUST_PASSWORD, encrypted = true) String trustPassword,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.CONNECT_TIMEOUT) String connectTimeout,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.SOCKET_TIMEOUT) String socketTimeout,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.USE_COOKIES) String useCookies,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.KEEP_ALIVE) String keepAlive,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.CONNECTIONS_MAX_PER_ROUTE) String connectionsMaxPerRoute,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.CONNECTIONS_MAX_TOTAL) String connectionsMaxTotal,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.HEADERS) String headers,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.RESPONSE_CHARACTER_SET) String responseCharacterSet,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.DESTINATION_FILE) String destinationFile,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.SOURCE_FILE, required = true) String sourceFile,
            @Param(value = io.cloudslang.content.httpclient.entities.HttpClientInputs.CHUNKED_REQUEST_ENTITY) String chunkedRequestEntity,
            @Param(value = InputNames.LANGUAGE) String language,
            @Param(value = InputNames.PROFILE) String profile,
            @Param(value = InputNames.TEXT_TYPE) String textType,
            @Param(value = InputNames.IMAGE_SOURCE) String imageSource,
            @Param(value = InputNames.CORRECT_ORIENTATION) String correctOrientation,
            @Param(value = InputNames.CORRECT_SKEW) String correctSkew,
            @Param(value = InputNames.READ_BARCODES) String readBarcodes,
            @Param(value = InputNames.EXPORT_FORMAT) String exportFormat,
            @Param(value = InputNames.WRITE_FORMATTING) String writeFormatting,
            @Param(value = InputNames.WRITE_RECOGNITION_VARIANTS) String writeRecognitionVariants,
            @Param(value = InputNames.WRITE_TAGS) String writeTags,
            @Param(value = InputNames.DESCRIPTION) String description,
            @Param(value = InputNames.PDF_PASSWORD, encrypted = true) String pdfPassword) {
        ProcessImageInput.Builder inputBuilder = new ProcessImageInput.Builder()
                .locationId(locationId)
                .applicationId(applicationId)
                .password(password)
                .timeToWait(timeToWait)
                .numberOfRetries(numberOfRetries)
                .proxyHost(proxyHost)
                .proxyPort(proxyPort)
                .proxyUsername(proxyUsername)
                .proxyPassword(proxyPassword)
                .trustAllRoots(trustAllRoots)
                .x509HostnameVerifier(x509HostnameVerifier)
                .trustKeystore(trustKeystore)
                .trustPassword(trustPassword)
                .connectTimeout(connectTimeout)
                .socketTimeout(socketTimeout)
                .useCookies(useCookies)
                .keepAlive(keepAlive)
                .connectionsMaxPerRoute(connectionsMaxPerRoute)
                .connectionsMaxTotal(connectionsMaxTotal)
                .headers(headers)
                .responseCharacterSet(responseCharacterSet)
                .destinationFile(destinationFile)
                .sourceFile(sourceFile)
                .chunkedRequestEntity(chunkedRequestEntity)
                .language(language)
                .profile(profile)
                .textType(textType)
                .imageSource(imageSource)
                .correctOrientation(correctOrientation)
                .correctSkew(correctSkew)
                .readBarcodes(readBarcodes)
                .exportFormat(exportFormat)
                .writeFormatting(writeFormatting)
                .writeRecognitionVariants(writeRecognitionVariants)
                .writeTags(writeTags)
                .description(description)
                .pdfPassword(pdfPassword);
        try {
            return new ProcessImageService().execute(inputBuilder.build());
        } catch (Exception ex) {
            return ResultUtils.fromException(ex);
        }
    }
}