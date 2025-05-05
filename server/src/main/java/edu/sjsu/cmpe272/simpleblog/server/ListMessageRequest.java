package edu.sjsu.cmpe272.simpleblog.server;

public class ListMessageRequest {
    private long limit = 10;
    private long next = -1;

    // Getters and Setters
    public long getLimit() {
        return limit;
    }


    public long getNext() {
        return next;
    }

}
