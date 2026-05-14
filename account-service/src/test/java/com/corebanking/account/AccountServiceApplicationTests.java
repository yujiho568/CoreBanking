package com.corebanking.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=localhost:29092")
class AccountServiceApplicationTests {

    @Test
    void contextLoads() {}
}
