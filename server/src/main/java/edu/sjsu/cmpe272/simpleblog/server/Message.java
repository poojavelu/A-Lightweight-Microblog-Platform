package edu.sjsu.cmpe272.simpleblog.server;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Message {
    @Id
    private String id;
    private long messageId;
    private String date;
    private String author;
    private String message;
    private String attachment;
    private String signature;

    public Message() {
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public long getMessageId() {return this.messageId;}
    public String getAuthor() {
        return this.author;
    }

    public String getDate() {
        return this.date;
    }

    public String getMessage() {
        return this.message;
    }

    public String getAttachment() {
        return this.attachment;
    }

    public String getSignature() {
        return this.signature;
    }
}