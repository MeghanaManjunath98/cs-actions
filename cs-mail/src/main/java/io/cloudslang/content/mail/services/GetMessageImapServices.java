/*
 * (c) Copyright 2019 EntIT Software LLC, a Micro Focus company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 *
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cloudslang.content.mail.services;

import com.sun.mail.util.ASCIIUtility;
import io.cloudslang.content.mail.entities.IMAPGetMailMessageInput;
import io.cloudslang.content.mail.entities.SimpleAuthenticator;
import io.cloudslang.content.mail.entities.StringOutputStream;
import io.cloudslang.content.mail.sslconfig.EasyX509TrustManager;
import io.cloudslang.content.mail.sslconfig.SSLUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnveloped;

import java.io.*;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import static io.cloudslang.content.mail.utils.Constants.*;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.bouncycastle.mail.smime.SMIMEUtil.toMimeBodyPart;

public class GetMessageImapServices {

    //Operation inputs

    private String port;
    private String protocol;
    private String username;
    private String password;
    private String folder;
    private boolean trustAllRoots;
    /**
     * The relative position of the message in the folder. Numbering starts from 1.
     */
    private int messageNumber;
    private boolean subjectOnly = true;
    private boolean enableSSL;
    private boolean enableTLS;
    private String keystore;
    private String keystorePassword;
    private String trustKeystoreFile;
    private String trustPassword;
    private String characterSet;
    private String decryptionKeystore;
    private String decryptionKeyAlias;
    private String decryptionKeystorePass;
    private boolean deleteUponRetrieval;
    private boolean markAsRead;
    private boolean decryptMessage;
    private int timeout = -1;
    private boolean verifyCertificate = false;

    private Store store;
    private RecipientId recId = null;
    private KeyStore ks = null;

    public Map<String, String> execute (IMAPGetMailMessageInput imapGetMailMessageInput) throws Exception {
        Map<String, String> result = new HashMap<>();
        try {
            processInputs(imapGetMailMessageInput);
            Message message = getMessage(imapGetMailMessageInput);

            if (decryptMessage) {
                addDecryptionSettings();
            }

            //delete message
            if (deleteUponRetrieval) {
                message.setFlag(Flags.Flag.DELETED, true);
            }
            if(markAsRead){
                message.setFlag(Flags.Flag.SEEN, true);
            }
            if (subjectOnly) {
                String subject;
                if ((characterSet != null) && (characterSet.trim().length() > 0)) { //need to force the decode charset
                    subject = message.getHeader(SUBJECT_HEADER)[0];
                    subject = changeHeaderCharset(subject, characterSet);
                    subject = MimeUtility.decodeText(subject);
                } else {
                    subject = message.getSubject();
                }
                if (subject == null) {
                    subject = "";
                }
                result.put(SUBJECT, MimeUtility.decodeText(subject));
                result.put(RETURN_RESULT, MimeUtility.decodeText(subject));
            } else {
                try {
                    // Get subject and attachedFileNames
                    if ((characterSet != null) && (characterSet.trim().length() > 0)) {
                        //need to force the decode charset
                        String subject = message.getHeader(SUBJECT_HEADER)[0];
                        subject = changeHeaderCharset(subject, characterSet);
                        result.put(SUBJECT, MimeUtility.decodeText(subject));
                        String attachedFileNames = changeHeaderCharset(getAttachedFileNames(message), characterSet);
                        result.put(ATTACHED_FILE_NAMES_RESULT, decodeAttachedFileNames(attachedFileNames));
                    } else {
                        //let everything as the sender intended it to be :)
                        String subject = message.getSubject();
                        if (subject == null) {
                            subject = "";
                        }
                        result.put(SUBJECT, MimeUtility.decodeText(subject));
                        result.put(ATTACHED_FILE_NAMES_RESULT,
                                decodeAttachedFileNames((getAttachedFileNames(message))));
                    }
                    // Get the message body
                    Map<String, String> messageByTypes = getMessageByContentTypes(message, characterSet);
                    String lastMessageBody = "";
                    if (!messageByTypes.isEmpty()) {
                        lastMessageBody = new LinkedList<>(messageByTypes.values()).getLast();
                    }
                    if (lastMessageBody == null) {
                        lastMessageBody = "";
                    }

                    result.put(BODY_RESULT, MimeUtility.decodeText(lastMessageBody));

                    String plainTextBody = messageByTypes.containsKey(TEXT_PLAIN) ? messageByTypes.get(TEXT_PLAIN) : "";
                    result.put(PLAIN_TEXT_BODY_RESULT, MimeUtility.decodeText(plainTextBody));

                    StringOutputStream stream = new StringOutputStream();
                    message.writeTo(stream);
                    result.put(RETURN_RESULT, stream.toString().replaceAll("" + (char) 0, ""));
                } catch (UnsupportedEncodingException except) {
                    throw new UnsupportedEncodingException("The given encoding (" + characterSet +
                            ") is invalid or not supported.");
                }
            }

            try {
                message.getFolder().close(true);
            } catch (Throwable ignore) {
            } finally {
                if (store != null)
                    store.close();
            }

            result.put(RETURN_CODE, SUCCESS_RETURN_CODE);
        } catch (Exception e) {
            if (e.toString().contains(UNRECOGNIZED_SSL_MESSAGE)) {
                throw new Exception(UNRECOGNIZED_SSL_MESSAGE_PLAINTEXT_CONNECTION);
            } else {
                throw e;
            }
        }
        return result;
    }

    protected Map<String, String> getMessageByContentTypes(Message message, String characterSet) throws Exception {

        Map<String, String> messageMap = new HashMap<>();

        if (message.isMimeType(TEXT_PLAIN)) {
            messageMap.put(TEXT_PLAIN, MimeUtility.decodeText(message.getContent().toString()));
        } else if (message.isMimeType(TEXT_HTML)) {
            messageMap.put(TEXT_HTML, MimeUtility.decodeText(convertMessage(message.getContent().toString())));
        } else if (message.isMimeType(MULTIPART_MIXED) || message.isMimeType(MULTIPART_RELATED)) {
            messageMap.put(MULTIPART_MIXED, extractMultipartMixedMessage(message, characterSet));
        } else {
            Object obj = message.getContent();
            Multipart mpart = (Multipart) obj;

            for (int i = 0, n = mpart.getCount(); i < n; i++) {

                Part part = mpart.getBodyPart(i);

                if (decryptMessage && part.getContentType() != null &&
                        part.getContentType().equals(ENCRYPTED_CONTENT_TYPE)) {
                    part = decryptPart((MimeBodyPart) part);
                }

                String disposition = part.getDisposition();
                String partContentType = part.getContentType().substring(0, part.getContentType().indexOf(";"));
                if (disposition == null) {
                    if (part.getContent() instanceof MimeMultipart) {
                        // multipart with attachment
                        MimeMultipart mm = (MimeMultipart) part.getContent();
                        for (int j = 0; j < mm.getCount(); j++) {
                            if (mm.getBodyPart(j).getContent() instanceof String) {
                                BodyPart bodyPart = mm.getBodyPart(j);
                                if ((characterSet != null) && (characterSet.trim().length() > 0)) {
                                    String contentType = bodyPart.getHeader(CONTENT_TYPE)[0];
                                    contentType = contentType
                                            .replace(contentType.substring(contentType.indexOf("=") + 1), characterSet);
                                    bodyPart.setHeader(CONTENT_TYPE, contentType);
                                }
                                String partContentType1 = bodyPart
                                        .getContentType().substring(0, bodyPart.getContentType().indexOf(";"));
                                messageMap.put(partContentType1,
                                        MimeUtility.decodeText(bodyPart.getContent().toString()));
                            }
                        }
                    } else {
                        //multipart - w/o attachment
                        //if the user has specified a certain characterSet we decode his way
                        if ((characterSet != null) && (characterSet.trim().length() > 0)) {
                            InputStream istream = part.getInputStream();
                            ByteArrayInputStream bis = new ByteArrayInputStream(ASCIIUtility.getBytes(istream));
                            int count = bis.available();
                            byte[] bytes = new byte[count];
                            count = bis.read(bytes, 0, count);
                            messageMap.put(partContentType,
                                    MimeUtility.decodeText(new String(bytes, 0, count, characterSet)));
                        } else {
                            messageMap.put(partContentType, MimeUtility.decodeText(part.getContent().toString()));
                        }
                    }
                }
            } //for
        } //else

        return messageMap;
    }


    private String extractMultipartMixedMessage(Message message, String characterSet) throws Exception {

        Object obj = message.getContent();
        Multipart mpart = (Multipart) obj;

        for (int i = 0, n = mpart.getCount(); i < n; i++) {

            Part part = mpart.getBodyPart(i);
            if (decryptMessage && part.getContentType() != null &&
                    part.getContentType().equals(ENCRYPTED_CONTENT_TYPE)) {
                part = decryptPart((MimeBodyPart) part);
            }
            String disposition = part.getDisposition();

            if (disposition != null) {
                // this means the part is not an inline image or attached file.
                continue;
            }

            if (part.isMimeType("multipart/related")) {
                // if related content then check it's parts

                String content = processMultipart(part);

                if (content != null) {
                    return content;
                }

            }

            if (part.isMimeType("multipart/alternative")) {
                return extractAlternativeContent(part);
            }

            if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                return part.getContent().toString();
            }

        }

        return null;
    }


    private String processMultipart(Part part) throws IOException,
            MessagingException {
        Multipart relatedparts = (Multipart) part.getContent();

        for (int j = 0; j < relatedparts.getCount(); j++) {

            Part rel = relatedparts.getBodyPart(j);

            if (rel.getDisposition() == null) {
                // again, if it's not an image or attachment(only those have disposition not null)

                if (rel.isMimeType("multipart/alternative")) {
                    // last crawl through the alternative formats.
                    return extractAlternativeContent(rel);
                }
            }
        }

        return null;
    }

    private String extractAlternativeContent(Part part) throws IOException, MessagingException {
        Multipart alternatives = (Multipart) part.getContent();

        Object content = "";

        for (int k = 0; k < alternatives.getCount(); k++) {
            Part alternative = alternatives.getBodyPart(k);
            if (alternative.getDisposition() == null) {
                content = alternative.getContent();
            }
        }

        return content.toString();
    }

    protected String convertMessage(String msg) throws Exception {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < msg.length(); i++) {
            char currentChar = msg.charAt(i);
            if (currentChar == '\n') {
                sb.append("<br>");
            } else {
                sb.append(currentChar);
            }

        }
        return sb.toString();
    }


    protected String decodeAttachedFileNames(String attachedFileNames) throws Exception {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        // splits the input into comma-separated chunks and decodes each chunk according to its encoding ...
        for (String fileName : attachedFileNames.split(STR_COMMA)) {
            sb.append(delimiter).append(MimeUtility.decodeText(fileName));
            delimiter = STR_COMMA;
        }
        // return the concatenation of the decoded chunks ...
        return sb.toString();
    }


    protected void processInputs(IMAPGetMailMessageInput imapGetMailMessageInput) throws Exception {

        String strHost = imapGetMailMessageInput.getHostname();
        if (isEmpty(strHost)) {
            throw new Exception(HOST_NOT_SPECIFIED);
        } else {
            imapGetMailMessageInput.setHostname(strHost.trim());
        }
        port = imapGetMailMessageInput.getPort();
        protocol = imapGetMailMessageInput.getProtocol();
        String strUsername = imapGetMailMessageInput.getUsername();
        if (isEmpty(strUsername)) {
            throw new Exception(USERNAME_NOT_SPECIFIED);
        } else {
            username = strUsername.trim();
        }
        String strPassword = imapGetMailMessageInput.getPassword();
        if (null == strPassword) {
            password = "";
        } else {
            password = strPassword.trim();
        }
        String strFolder = imapGetMailMessageInput.getFolder();
        if (isEmpty(strFolder)) {
            throw new Exception(FOLDER_NOT_SPECIFIED);
        } else {
            folder = strFolder.trim();
        }
        String trustAll = imapGetMailMessageInput.getTrustAllRoots();
        // Default value of trustAllRoots is true
        trustAllRoots = !(null != trustAll && trustAll.equalsIgnoreCase(STR_FALSE));
        String strMessageNumber = imapGetMailMessageInput.getMessageNumber();
        if (isEmpty(strMessageNumber)) {
            throw new Exception(MESSAGE_NUMBER_NOT_SPECIFIED);
        } else {
            messageNumber = Integer.parseInt(strMessageNumber);
        }
        String strSubOnly = imapGetMailMessageInput.getSubjectOnly();
        // Default value of subjectOnly is false
        subjectOnly = (strSubOnly != null && strSubOnly.equalsIgnoreCase(STR_TRUE));
        String strEnableSSL = imapGetMailMessageInput.getEnableSSL();
//        // Default value of enableSSL is false;
        keystore = defaultIfEmpty(imapGetMailMessageInput.getKeystore(), DEFAULT_JAVA_KEYSTORE);
        keystorePassword = imapGetMailMessageInput.getKeystorePassword();
        trustKeystoreFile = defaultIfEmpty(imapGetMailMessageInput.getTrustKeystore(), DEFAULT_JAVA_KEYSTORE);
        enableSSL = (null != strEnableSSL && strEnableSSL.equalsIgnoreCase(STR_TRUE));
        String strEnableTLS = imapGetMailMessageInput.getEnableTLS();
        enableTLS = (null != strEnableTLS && strEnableTLS.equalsIgnoreCase(STR_TRUE));
        trustPassword = imapGetMailMessageInput.getTrustPassword();
        characterSet = imapGetMailMessageInput.getCharacterSet();
        String strDeleteUponRetrieval = imapGetMailMessageInput.getDeleteUponRetrieval();
        // Default value for deleteUponRetrieval is false
        deleteUponRetrieval = (null != strDeleteUponRetrieval && strDeleteUponRetrieval.equalsIgnoreCase(STR_TRUE));

        String markMailAsReadInput = imapGetMailMessageInput.getMarkMessageAsRead();
        // Default value for markmailasread is false
        markAsRead = (null != markMailAsReadInput && markMailAsReadInput.equalsIgnoreCase(STR_TRUE));


        if (messageNumber < 1) {
            throw new Exception(MESSAGES_ARE_NUMBERED_STARTING_AT_1);
        }
        if ((isEmpty(protocol)) && (isEmpty(port))) {
            throw new Exception(SPECIFY_PORT_OR_PROTOCOL_OR_BOTH);
        } else if ((protocol != null && !"".equals(protocol)) && (!protocol.equalsIgnoreCase(IMAP)) &&
                (!protocol.equalsIgnoreCase(POP3)) && (!protocol.equalsIgnoreCase(IMAP_4)) &&
                (isEmpty(port))) {
            throw new Exception(SPECIFY_PORT_FOR_PROTOCOL);
        } else if ((isEmpty(protocol)) && StringUtils.isEmpty(port) &&
                (!port.equalsIgnoreCase(IMAP_PORT)) && (!port.equalsIgnoreCase(POP3_PORT))) {
            throw new Exception(SPECIFY_PROTOCOL_FOR_GIVEN_PORT);
        } else if ((isEmpty(protocol)) && (port.trim().equalsIgnoreCase(IMAP_PORT))) {
            protocol = IMAP;
        } else if ((isEmpty(protocol)) && (port.trim().equalsIgnoreCase(POP3_PORT))) {
            protocol = POP3;
        } else if ((protocol.trim().equalsIgnoreCase(POP3)) && (isEmpty(port))) {
            port = POP3_PORT;
        } else if ((protocol.trim().equalsIgnoreCase(IMAP)) && (isEmpty(port))) {
            port = IMAP_PORT;
        } else if ((protocol.trim().equalsIgnoreCase(IMAP_4)) && (isEmpty(port))) {
            port = IMAP_PORT;
        }

        //The protocol should be given in lowercase to be recognised.
        protocol = protocol.toLowerCase();
        if (protocol.trim().equalsIgnoreCase(IMAP_4)) {
            protocol = IMAP;
        }

        this.decryptionKeystore = imapGetMailMessageInput.getDecryptionKeystore();
        if (isNotEmpty(this.decryptionKeystore)) {
            if (!decryptionKeystore.startsWith(HTTP)) {
                decryptionKeystore = FILE + decryptionKeystore;
            }

            decryptMessage = true;
            decryptionKeyAlias = imapGetMailMessageInput.getDecryptionKeyAlias();
            if (null == decryptionKeyAlias) {
                decryptionKeyAlias = "";
            }
            decryptionKeystorePass = imapGetMailMessageInput.getDecryptionKeystorePassword();
            if (null == decryptionKeystorePass) {
                decryptionKeystorePass = "";
            }

        } else {
            decryptMessage = false;
        }

        String timeout = imapGetMailMessageInput.getTimeout();
        if (isNotEmpty(timeout)) {
            this.timeout = Integer.parseInt(timeout);
            if (this.timeout <= 0) {
                throw new Exception("timeout value must be a positive number");
            }
            this.timeout *= ONE_SECOND; //timeouts in seconds
        }

        String verifyCertStr = imapGetMailMessageInput.getVerifyCertificate();
        if (!isEmpty(verifyCertStr)) {
            verifyCertificate = Boolean.parseBoolean(verifyCertStr);
        }

    }

    private void addDecryptionSettings() throws Exception {
        char[] smimePw = new String(decryptionKeystorePass).toCharArray();

        Security.addProvider(new BouncyCastleProvider());
        ks = KeyStore.getInstance(PKCS_KEYSTORE_TYPE, BOUNCY_CASTLE_PROVIDER);

        InputStream decryptionStream = new URL(decryptionKeystore).openStream();
        try {
            ks.load(decryptionStream, smimePw);
        } finally {
            decryptionStream.close();
        }

        if ("".equals(decryptionKeyAlias)) {
            Enumeration aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = (String) aliases.nextElement();

                if (ks.isKeyEntry(alias)) {
                    decryptionKeyAlias = alias;
                }
            }

            if ("".equals(decryptionKeyAlias)) {
                throw new Exception(PRIVATE_KEY_ERROR_MESSAGE);
            }
        }

        //
        // find the certificate for the private key and generate a
        // suitable recipient identifier.
        //
        X509Certificate cert = (X509Certificate) ks.getCertificate(decryptionKeyAlias);
        if (null == cert) {
            throw new Exception("Can't find a key pair with alias \"" + decryptionKeyAlias +
                    "\" in the given keystore");
        }
        if (verifyCertificate) {
            cert.checkValidity();
        }

        recId = new RecipientId();
        recId.setSerialNumber(cert.getSerialNumber());
        recId.setIssuer(cert.getIssuerX500Principal().getEncoded());
    }

    protected Message getMessage(IMAPGetMailMessageInput imapGetMailMessageInput) throws Exception {
        store = createMessageStore(imapGetMailMessageInput);
        Folder folder = store.getFolder(this.folder);
        if (!folder.exists()) {
            throw new Exception(THE_SPECIFIED_FOLDER_DOES_NOT_EXIST_ON_THE_REMOTE_SERVER);
        }
        folder.open(getFolderOpenMode());
        if (messageNumber > folder.getMessageCount()) {
            throw new IndexOutOfBoundsException("message value was: " + messageNumber + " there are only " +
                    folder.getMessageCount() + COUNT_MESSAGES_IN_FOLDER_ERROR_MESSAGE);
        }
        return folder.getMessage(messageNumber);
    }


    protected Store createMessageStore(IMAPGetMailMessageInput imapGetMailMessageInput) throws Exception {
        Properties props = new Properties();
        if (timeout > 0) {
            props.put("mail." + protocol + ".timeout", timeout);
            setPropertiesProxy(props,imapGetMailMessageInput);
        }
        Authenticator auth = new SimpleAuthenticator(username, password);
        Store store;
        if (enableTLS || enableSSL) {
            addSSLSettings(trustAllRoots, keystore, keystorePassword, trustKeystoreFile, trustPassword);
        }
        if (enableTLS) {
            store = tryTLSOtherwiseTrySSL(props, auth, imapGetMailMessageInput);
        } else if (enableSSL) {
            store = connectUsingSSL(props, auth,imapGetMailMessageInput);
        } else {
            store = configureStoreWithoutSSL(props, auth, imapGetMailMessageInput);
            store.connect();
        }

        return store;
    }

    private Store tryTLSOtherwiseTrySSL(Properties props, Authenticator auth,IMAPGetMailMessageInput imapGetMailMessageInput) throws MessagingException {
        Store store = configureStoreWithTLS(props, auth);
        try {
            store.connect(imapGetMailMessageInput.getHostname(), username, password);
        } catch (Exception e) {
            if (enableSSL) {
                clearTLSProperties(props);
                store = connectUsingSSL(props, auth,imapGetMailMessageInput);
            } else {
                throw e;
            }
        }
        return store;
    }

    protected Store configureStoreWithSSL(Properties props, Authenticator auth, IMAPGetMailMessageInput imapGetMailMessageInput) throws NoSuchProviderException {
        props.setProperty("mail." + protocol + ".socketFactory.class", SSL_FACTORY);
        props.setProperty("mail." + protocol + ".socketFactory.fallback", STR_FALSE);
        props.setProperty("mail." + protocol + ".port", port);
        props.setProperty("mail." + protocol + ".socketFactory.port", port);
        URLName url = new URLName(protocol, imapGetMailMessageInput.getHostname(), Integer.parseInt(port), "", username, password);
        Session session = Session.getInstance(props, auth);
        return session.getStore(url);
    }

    private Store connectUsingSSL(Properties props, Authenticator auth,IMAPGetMailMessageInput imapGetMailMessageInput) throws MessagingException {
        Store store = configureStoreWithSSL(props, auth, imapGetMailMessageInput);
        store.connect();
        return store;
    }

    private void clearTLSProperties(Properties props) {
        props.remove("mail." + protocol + ".ssl.enable");
        props.remove("mail." + protocol + ".starttls.enable");
        props.remove("mail." + protocol + ".starttls.required");
    }


    protected Store configureStoreWithTLS(Properties props, Authenticator auth) throws NoSuchProviderException {
        props.setProperty("mail." + protocol + ".ssl.enable", STR_FALSE);
        props.setProperty("mail." + protocol + ".starttls.enable", STR_TRUE);
        props.setProperty("mail." + protocol + ".starttls.required", STR_TRUE);
        Session session = Session.getInstance(props, auth);
        return session.getStore(protocol + SECURE_SUFFIX_FOR_POP3_AND_IMAP);
    }

    protected void addSSLSettings(boolean trustAllRoots, String keystore, String keystorePassword,
                                  String trustKeystore, String trustPassword) throws Exception {
        boolean useClientCert = false;
        boolean useTrustCert = false;

        String separator = getSystemFileSeparator();
        String javaKeystore = getSystemJavaHome() + separator + "lib" + separator + "security" + separator + "cacerts";
        if (keystore.length() == 0 && !trustAllRoots) {
            boolean storeExists = new File(javaKeystore).exists();
            keystore = (storeExists) ? FILE + javaKeystore : null;
            if (null != keystorePassword) {
                if ("".equals(keystorePassword)) {
                    keystorePassword = DEFAULT_PASSWORD_FOR_STORE;
                }
            }
            useClientCert = storeExists;
        } else {
            if (!trustAllRoots) {
                if (!keystore.startsWith(HTTP)) {
                    keystore = FILE + keystore;
                }
                useClientCert = true;
            }
        }
        if (trustKeystore.length() == 0 && !trustAllRoots) {
            boolean storeExists = new File(javaKeystore).exists();
            trustKeystore = (storeExists) ? FILE + javaKeystore : null;
            if (storeExists) {
                if (isEmpty(trustPassword)) {
                    trustPassword = DEFAULT_PASSWORD_FOR_STORE;
                }
            } else {
                trustPassword = null;
            }

            useTrustCert = storeExists;
        } else {
            if (!trustAllRoots) {
                if (!trustKeystore.startsWith(HTTP)) {
                    trustKeystore = FILE + trustKeystore;
                }
                useTrustCert = true;
            }
        }

        TrustManager[] trustManagers = null;
        KeyManager[] keyManagers = null;

        if (trustAllRoots) {
            trustManagers = new TrustManager[]{new EasyX509TrustManager()};
        }

        if (useTrustCert) {
            KeyStore trustKeyStore = SSLUtils.createKeyStore(new URL(trustKeystore), trustPassword);
            trustManagers = SSLUtils.createAuthTrustManagers(trustKeyStore);
        }
        if (useClientCert) {
            KeyStore clientKeyStore = SSLUtils.createKeyStore(new URL(keystore), keystorePassword);
            keyManagers = SSLUtils.createKeyManagers(clientKeyStore, keystorePassword);
        }

        SSLContext context = SSLContext.getInstance(SSL);
        context.init(keyManagers, trustManagers, new SecureRandom());
        SSLContext.setDefault(context);
    }

    protected String getSystemFileSeparator() {
        return System.getProperty("file.separator");
    }

    protected void setPropertiesProxy(Properties prop , IMAPGetMailMessageInput imapGetMailMessageInput){
        if(protocol.contains("imap"))
        {
            prop.setProperty("mail.imaps.proxy.host", imapGetMailMessageInput.getProxyHost());
            prop.setProperty("mail.imaps.proxy.port", imapGetMailMessageInput.getProxyPort());
            prop.setProperty("mail.imaps.proxy.user",  imapGetMailMessageInput.getProxyUsername());
            prop.setProperty("mail.imaps.proxy.password",  imapGetMailMessageInput.getProxyPassword());

        }
        if(protocol.contains("pop")){
            prop.setProperty("mail.pop3.proxy.host", imapGetMailMessageInput.getProxyHost());
            prop.setProperty("mail.pop3.proxy.port", imapGetMailMessageInput.getProxyPort());
            prop.setProperty("mail.pop3.proxy.user", imapGetMailMessageInput.getProxyUsername());
            prop.setProperty("mail.pop3.proxy.password", imapGetMailMessageInput.getProxyPassword());
        }

    }


    protected Store configureStoreWithoutSSL(Properties props, Authenticator auth,IMAPGetMailMessageInput imapGetMailMessageInput) throws NoSuchProviderException {
        props.put("mail." + protocol + ".host", imapGetMailMessageInput.getHostname());
        props.put("mail." + protocol + ".port", port);
        Session session = Session.getInstance(props, auth);
        return session.getStore(protocol);
    }

    protected String getSystemJavaHome() {
        return System.getProperty("java.home");
    }

    protected int getFolderOpenMode() {
        return Folder.READ_WRITE;
    }

    /**
     * This method addresses the mail headers which contain encoded words. The syntax for an encoded word is defined in
     * RFC 2047 section 2: http://www.faqs.org/rfcs/rfc2047.html In some cases the header is marked as having a certain
     * charset but at decode not all the characters a properly decoded. This is why it can be useful to force it to
     * decode the text with a different charset.
     * For example when sending an email using Mozilla Thunderbird and JIS X 0213 characters the subject and attachment
     * headers are marked as =?Shift_JIS? but the JIS X 0213 characters are only supported in windows-31j.
     * <p/>
     * This method replaces the charset tag of the header with the new charset provided by the user.
     *
     * @param header     - The header in which the charset will be replaced.
     * @param newCharset - The new charset that will be replaced in the given header.
     * @return The header with the new charset.
     */
    public String changeHeaderCharset(String header, String newCharset) {
        //match for =?charset?
        return header.replaceAll("=\\?[^\\(\\)<>@,;:/\\[\\]\\?\\.= ]+\\?", "=?" + newCharset + "?");
    }

    protected String getAttachedFileNames(Part part) throws Exception {
        String fileNames = "";
        Object content = part.getContent();
        if (!(content instanceof Multipart)) {
            if (decryptMessage && part.getContentType() != null &&
                    part.getContentType().equals(ENCRYPTED_CONTENT_TYPE)) {
                part = decryptPart((MimeBodyPart) part);
            }
            // non-Multipart MIME part ...
            // is the file name set for this MIME part? (i.e. is it an attachment?)
            if (part.getFileName() != null && !part.getFileName().equals("") && part.getInputStream() != null) {
                String fileName = part.getFileName();
                // is the file name encoded? (consider it is if it's in the =?charset?encoding?encoded text?= format)
                if (fileName.indexOf('?') == -1) {
                    // not encoded  (i.e. a simple file name not containing '?')-> just return the file name
                    return fileName;
                }
                // encoded file name -> remove any chars before the first "=?" and after the last "?="
                return fileName.substring(fileName.indexOf("=?"), fileName.length() -
                        ((new StringBuilder(fileName)).reverse()).indexOf("=?"));
            }
        } else {
            // a Multipart type of MIME part
            Multipart mpart = (Multipart) content;
            // iterate through all the parts in this Multipart ...
            for (int i = 0, n = mpart.getCount(); i < n; i++) {
                if (!"".equals(fileNames)) {
                    fileNames += STR_COMMA;
                }
                // to the list of attachments built so far append the list of attachments in the current MIME part ...
                fileNames += getAttachedFileNames(mpart.getBodyPart(i));
            }
        }
        return fileNames;
    }
    private MimeBodyPart decryptPart(MimeBodyPart part) throws Exception {

        SMIMEEnveloped smimeEnveloped = new SMIMEEnveloped(part);
        RecipientInformationStore recipients = smimeEnveloped.getRecipientInfos();
        RecipientInformation recipient = recipients.get(recId);

        if (null == recipient) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("This email wasn't encrypted with \"" + recId.toString() + "\".\n");
            errorMessage.append(ENCRYPT_RECID);

            for (Object rec : recipients.getRecipients()) {
                if (rec instanceof RecipientInformation) {
                    RecipientId recipientId = ((RecipientInformation) rec).getRID();
                    errorMessage.append("\"" + recipientId.toString() + "\"\n");
                }
            }
            throw new Exception(errorMessage.toString());
        }

        return toMimeBodyPart(recipient.getContent(ks.getKey(decryptionKeyAlias, null), BOUNCY_CASTLE_PROVIDER));
    }
}