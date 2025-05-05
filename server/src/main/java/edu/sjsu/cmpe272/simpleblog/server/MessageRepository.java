package edu.sjsu.cmpe272.simpleblog.server;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    @Query("{ 'messageId' : { $gte: ?0, $lte: ?1 } }")
    List<Message> findMessagesIncludingBoundaries(long startMessageId, long endMessageId);

}
