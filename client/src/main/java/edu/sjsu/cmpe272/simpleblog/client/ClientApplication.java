package edu.sjsu.cmpe272.simpleblog.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;

import static picocli.CommandLine.Parameters.NULL_VALUE;

@SpringBootApplication
@Command
public class ClientApplication implements CommandLineRunner, ExitCodeGenerator {
    // Endpoints of service
    private static final String WEBSITE = "https://cmpe272.mooo.com";
    private static final String CREATE_USER_ENDPOINT = WEBSITE + "/user/create";
    private static final String POST_MESSAGE_ENDPOINT = WEBSITE + "/messages/create";
    private static final String LIST_MESSAGE_ENDPOINT = WEBSITE + "/messages/list";
    private static final String PRIVATE_KEY_FILE = "mb.ini";
    private static final Logger logger = Logger.getLogger("MicroBlogClient");
    @Autowired
    CommandLine.IFactory iFactory;

    //List command
    @Command
    public boolean list(@Parameters(defaultValue = NULL_VALUE) Long startingId,
                        @Parameters(defaultValue = NULL_VALUE) Long countNumber,
                        @Option(names={"--save-attachment"}) boolean saveAttachment) throws JSONException, IOException {
        JSONObject jsonObject = new JSONObject();
        long nextId = startingId != null ? startingId : -1;
        long remaining = countNumber != null ? countNumber : 10;
        ArrayList<String> message_list = new ArrayList<String>();
        while (remaining > 0) {
            long limit = Math.min(remaining, 20);
            try {
                jsonObject.put("limit", limit);
                jsonObject.put("next", nextId);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            HttpResponse<String> response = postRequest(LIST_MESSAGE_ENDPOINT, jsonObject);
            logger.info("Response: " + response.body());
            String jsonString= response.body();
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = jsonArray.length() - 1; i >= 0; i--) {
                JSONObject msgJsonObject = jsonArray.getJSONObject(i);
                String message = msgJsonObject.getString("messageId")+": "+msgJsonObject.get("date")+" "+msgJsonObject.get("author")+" says"+" \""+msgJsonObject.get("message")+ "\"";
                boolean hasAttachment = !msgJsonObject.getString("attachment").isEmpty();
                if (hasAttachment) {
                    message += " 📎";
                }
               message_list.add(message);
               if(saveAttachment && hasAttachment) {
                   byte[] decodedBytes = Base64.getDecoder().decode(msgJsonObject.getString("attachment"));
                   Path path = Paths.get(msgJsonObject.getString("messageId") + ".out");
                   Files.write(path, decodedBytes);
               }
                if(i==0){
                    nextId= Long.parseLong(msgJsonObject.getString("messageId"))-1;
                }
            }
            remaining -= limit;
        }
        logger.info("Message list: " + message_list);
        message_list.forEach(System.out::println);
        return true;
    }

    //post message command
    @Command
    public boolean post(@Parameters String message, @Parameters(defaultValue = NULL_VALUE) String attachment) {
        JSONObject jsonObject = new JSONObject();
        String author = "";
        try {
            author = getAuthor();
        } catch (FileNotFoundException e) {
            logger.warning("Private key file mb.ini not found. Please create an user before posting");
            return false;
        } catch (JSONException e) {
            logger.warning("Invalid mb.ini file. Please create a new user id to fix it.");
            return false;
        }
        try {
            jsonObject.put("date", LocalDateTime.now().toString());
            jsonObject.put("author", author);
            jsonObject.put("message", message);
            if (attachment != null) {
                logger.info("And upload " + attachment);
                File file = new File(attachment);
                try {
                    String base64EncodedString = encodeFileToBase64(file);
                    jsonObject.put("attachment", base64EncodedString);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // TODO Read the file and add the contents
            } else {
                jsonObject.put("attachment", "");
            }
            String privateKeyString = getPrivateKey();
            PrivateKey privateKey = null;
            try {
                privateKey = convertStringToPrivateKey(privateKeyString);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String jsonStr = "";
            try {
                jsonStr = String.format(
                        "{\"date\":\"%s\",\"author\":\"%s\",\"message\":\"%s\",\"attachment\":\"%s\"}",
                        jsonObject.get("date"),
                        jsonObject.get("author"),
                        jsonObject.get("message"),
                        jsonObject.get("attachment")
                );
            } catch (JSONException e) {
                logger.warning("Message has to contain all of date, author, message, attachment");
                return false;
            }
            //Generate signature
                try {
                    byte[] signature = signData(jsonStr.getBytes(), privateKey);
                    jsonObject.put("signature", Base64.getEncoder().encodeToString(signature));
                    logger.info("Generated signature: " + Base64.getEncoder().encodeToString(signature));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
        } catch (JSONException e) {
            logger.warning(String.format("Error encountered while constructing message json: %s", e.toString()));
            return false;
        } catch (FileNotFoundException e) {
            logger.warning("Private key file mb.ini not found. Please create an user before posting");
            return false;
        }
        HttpResponse<String> response = postRequest(POST_MESSAGE_ENDPOINT, jsonObject);
        logger.info("Response: " + response.body());
        System.out.println(response.body());
        return true;
    }

    //create user command
    @Command
    int create(@Parameters String id) throws IOException {
        File file = new File(PRIVATE_KEY_FILE);
        if (file.exists()) {
            logger.warning("The mb.ini file already exists.");
            return 0;
        }
        Matcher matcher = Pattern.compile("^[a-z0-9]+$").matcher(id);
        if(!matcher.matches()){
            logger.warning("User Id must contain only lowercase letters and numbers");
            return 0;
        }
        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        Base64.Encoder encoder = Base64.getMimeEncoder();
        String privateKey = encoder.encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = encoder.encodeToString(keyPair.getPublic().getEncoded());
        String pemPublicKey = "-----BEGIN PUBLIC KEY-----\n" +publicKey+"\n-----END PUBLIC KEY-----\n";
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("user", id);
            jsonObject.put("publicKey", pemPublicKey);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        logger.info(String.format("Sending post request %s to endpoint %s", jsonObject, CREATE_USER_ENDPOINT));
        HttpResponse<String> response = postRequest(CREATE_USER_ENDPOINT, jsonObject);
        if (response.body().contains("error")) {
            System.out.println(response.body());
            return 0;
        }
        try {
            jsonObject.put("privateKey", privateKey);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        try (FileWriter fileWriter = new FileWriter(PRIVATE_KEY_FILE)) {
            fileWriter.write(jsonObject.toString());
        } catch (IOException e) {
            logger.warning("An error occurred while writing private key file.");
            logger.warning(e.getMessage());
        }
        logger.info("Successfully wrote to the file");
        System.out.println(response.body());
        return 0;
    }
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }
    int exitCode;
    @Override
    public void run(String... args) throws Exception {
        exitCode = new CommandLine(this, iFactory).execute(args);
    }
    @Override
    public int getExitCode() {
        return exitCode;
    }
    //encode attachment to Base64
    public static String encodeFileToBase64(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }
    //api request
    private HttpResponse<String> postRequest(String endpoint, JSONObject jsonObject) {
        String jsonRequest = jsonObject.toString();
        logger.info(String.format("Sending post request %s to endpoint %s", jsonRequest, endpoint));
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .headers("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.warning(String.format("Received exception while sending request: %s", e.getMessage()));
        }
        return response;
    }
    private JSONObject readMbIniFile() throws FileNotFoundException, JSONException {
        BufferedReader reader = new BufferedReader(new FileReader(PRIVATE_KEY_FILE));
        String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        return new JSONObject(content);
    }
    private String getAuthor() throws FileNotFoundException, JSONException {
        JSONObject jsonObject = readMbIniFile();
        return jsonObject.getString("user");
    }
    private String getPrivateKey() throws FileNotFoundException, JSONException {
        JSONObject jsonObject = readMbIniFile();
        return jsonObject.getString("privateKey");
    }
    //generate signature
    public static byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(data);
        return signer.sign();
    }
    //convert string to private key
    public static PrivateKey convertStringToPrivateKey(String base64PrivateKey) throws Exception {
        // Normalize and decode the Base64 string
        String normalizedBase64PrivateKey = base64PrivateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", ""); // Remove all whitespace characters (e.g., newline)

        byte[] decodedKey = Base64.getDecoder().decode(normalizedBase64PrivateKey);
        // Reconstruct and generate the private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
}
