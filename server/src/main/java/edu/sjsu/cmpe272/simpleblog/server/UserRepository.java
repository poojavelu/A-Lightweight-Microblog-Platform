package edu.sjsu.cmpe272.simpleblog.server;


import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    User findUserByUser(String user);

    boolean existsByUser(String user);
}
