package edu.sjsu.cmpe272.simpleblog.server;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    @Autowired
    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public Message addMessage(Message message) {
        return messageRepository.save(message);
    }

    public List<Message> findMessagesInRange(long startMessageId, long endMessageId) {
        return messageRepository.findMessagesIncludingBoundaries(startMessageId, endMessageId);
    }

    public boolean verifySign(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
    public byte[] decodeBase64ToBytes(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }
    public PublicKey getPublicKeyFromString(String base64PublicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String normalizedBase64PublicKey = base64PublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""); // Remove all whitespace characters (e.g., newline)
        byte[] publicBytes = Base64.getDecoder().decode(normalizedBase64PublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
