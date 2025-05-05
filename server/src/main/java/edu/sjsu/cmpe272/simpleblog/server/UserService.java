package edu.sjsu.cmpe272.simpleblog.server;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User addUser(String name, String publicKey) {
        User user = new User(name, publicKey);
        return userRepository.save(user);
    }

    public User getUserByName(String name) {
        return userRepository.findUserByUser(name);
    }

    public boolean checkUserExists(String name) {
        return userRepository.existsByUser(name);
    }
}
