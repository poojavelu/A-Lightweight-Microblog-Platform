package edu.sjsu.cmpe272.simpleblog.server.controller;
import edu.sjsu.cmpe272.simpleblog.server.ListMessageRequest;
import edu.sjsu.cmpe272.simpleblog.server.MessageSequenceService;
import edu.sjsu.cmpe272.simpleblog.server.MessageService;
import edu.sjsu.cmpe272.simpleblog.server.UserService;
import edu.sjsu.cmpe272.simpleblog.server.Message;
import edu.sjsu.cmpe272.simpleblog.server.User;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Welcome {
    private final MessageService messageService;
    private final MessageSequenceService messageSequenceService;
    private final UserService userService;

    private static final Logger logger = Logger.getLogger("MicroBlog");

    @Autowired
    public Welcome(UserService userService, MessageService messageService, MessageSequenceService messageSequenceService)  {
        this.userService = userService;
        this.messageService = messageService;
        this.messageSequenceService = messageSequenceService;
    }

    @GetMapping("/")
    ResponseEntity<String> getWelcome() {
        return ResponseEntity.ok("Welcome!");
    }

    @PostMapping("/user/create")
    public String addPerson(@RequestBody User user) {
        logger.info(String.format("Adding user with user name: %s, public key: %s", user.getUser(), user.getPublicKey()));
        Matcher matcher = Pattern.compile("^[a-z0-9]+$").matcher(user.getUser());
        if(!matcher.matches()) {
            logger.info("User Id must contain only lowercase letters and numbers.");
            return "{ \"error\": \"User Id must contain only lowercase letters and numbers.\" }";
        }
        boolean exists = userService.checkUserExists(user.getUser());
        if (exists) {
            String message = String.format("{\"error\": \"User %s already exists\"}", user.getUser());
            logger.info(message);
            return message;
        }
        User userDetails= userService.addUser(user.getUser(), user.getPublicKey());
        logger.info(String.format("userName=%s and publicKey=%s", userDetails.getUser(), userDetails.getPublicKey()));
        return "{ message: \"welcome\" }";
    }

    @PostMapping("/messages/list")
    public String listMessage(@RequestBody ListMessageRequest request) throws JSONException {
        long limit = request.getLimit();
        long next = request.getNext();
        logger.info(String.format("Received list message request with limit=%d and next=%d", limit, next));
        if (limit > 20) {
            return "{\"error\": \"Limit cannot be greater than 20\"}";
        }
        if (limit <= 0) {
            return "{\"error\": \"Limit cannot be less than or equal to 0\"}";
        }
        if (next == -1) {
            next = messageSequenceService.getSequence("message");
        }
        long startMessageId = 0;
        if (limit < next) {
            startMessageId = next - limit + 1;
        }
        logger.info(String.format("Finding messages in range (%d, %d)", startMessageId, next));
        List<Message> messages = messageService.findMessagesInRange(startMessageId, next);
        logger.info(String.format("Returned %d messages", messages.size()));
        JSONArray jsonArray = new JSONArray();
        logger.info(String.format("Array length %d", jsonArray.length()));
        for (Message message : messages) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("messageId",message.getMessageId());
            jsonObject.put("date",message.getDate());
            jsonObject.put("author",message.getAuthor());
            jsonObject.put("message",message.getMessage());
            jsonObject.put("attachment",message.getAttachment());
            jsonObject.put("signature",message.getSignature());
            jsonArray.put(jsonObject);
        }
        return jsonArray.toString();
    }

    @PostMapping("/messages/create")
    public String createMessage(@RequestBody Message message) {
        logger.info(String.format("Posting message: Message Id: %d\nDate: %s\nAuthor: %s\nMessage: %s\nAttachment: %s\nSignature: %s\n", message.getMessageId(), message.getDate(), message.getAuthor(), message.getMessage(), message.getAttachment(), message.getSignature()));
        String author= message.getAuthor();
        String publicKeyString = userService.getUserByName(author).getPublicKey();
        PublicKey publicKey;
        try {
            publicKey=messageService.getPublicKeyFromString(publicKeyString);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
        byte[] signatureBytes = messageService.decodeBase64ToBytes(message.getSignature());
        // Verify the signature
        boolean isCorrect;
        String jsonStr = String.format(
                "{\"date\":\"%s\",\"author\":\"%s\",\"message\":\"%s\",\"attachment\":\"%s\"}",
                message.getDate(),
                message.getAuthor(),
                message.getMessage(),
                message.getAttachment()
        );
        try {
            isCorrect = messageService.verifySign(
                    jsonStr.getBytes(),
                    signatureBytes,
                    publicKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        logger.info("Signature verification result: " + isCorrect);
        if(isCorrect) {
            message.setMessageId(messageSequenceService.getNextSequence("message"));
            Message result= messageService.addMessage(message);
            return "{\"message-id\": " +result.getMessageId()+"}";
        }else {
            return "{\"error\": \"signature didn't match\"}";
        }
    }

    @GetMapping("/user/{username}/public-key")
    public String listMessage(@PathVariable("username") String username) {
        if (userService.checkUserExists(username)) {
            return userService.getUserByName(username).getPublicKey();
        }
        return "{\"error\": \"User " + username + " does not exist\"}";
    }
}
