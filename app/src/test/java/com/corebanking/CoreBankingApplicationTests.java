package com.corebanking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CoreBankingApplicationTests {

    @Test
    void contextLoads() {
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext context;

    @Test
    void defaultProfileDoesNotExposeMockDemo() {
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.corebanking.openbanking.controller.MockDemoController.class)).isEmpty();
    }
}
