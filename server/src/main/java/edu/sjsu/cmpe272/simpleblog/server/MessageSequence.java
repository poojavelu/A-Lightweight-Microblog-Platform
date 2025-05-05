package edu.sjsu.cmpe272.simpleblog.server;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class MessageSequence {
    @Id
    private String id;

    private long seq;

    public long getSeq() {
        return this.seq;
    }
}