package edu.sjsu.cmpe272.simpleblog.server;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class MessageSequenceService {

    @Autowired private MongoOperations mongo;

    public long getNextSequence(String seqName)
    {
        MessageSequence counter = mongo.findAndModify(
                query(where("_id").is(seqName)),
                new Update().inc("seq",1),
                options().returnNew(true).upsert(true),
                MessageSequence.class);
        return counter.getSeq();
    }

    public long getSequence(String seqName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(seqName));
        MessageSequence counter = mongo.findOne(query, MessageSequence.class);
        return counter.getSeq();
    }
}
