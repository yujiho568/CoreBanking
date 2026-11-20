package com.corebanking.openbanking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenBankingProperties.class)
public class OpenBankingConfig {
}
