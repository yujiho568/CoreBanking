package com.corebanking.openbanking.service;
import com.corebanking.openbanking.config.OpenBankingProperties;
import com.corebanking.openbanking.infrastructure.KftcOpenBankingAdapter;
import com.corebanking.openbanking.exception.OpenBankingProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Tag("performance")
class ExternalHttpBenchmark {
    @Test void measuresLocalDelayedProviderAndChecksTimeoutAndErrors() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger calls=new AtomicInteger(),delay=new AtomicInteger(30),status=new AtomicInteger(200);
        server.createContext("/v2.0/account/balance/fin_num",exchange->{
            calls.incrementAndGet();
            try { Thread.sleep(delay.get()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body="{\"rsp_code\":\"A0000\",\"bank_rsp_code\":\"000\",\"balance_amt\":\"1000\",\"available_amt\":\"900\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            try { exchange.sendResponseHeaders(status.get(),body.length);exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            var adapter=adapter(uri,Duration.ofSeconds(2));
            var cache=new ExternalQueryCache(new ConcurrentMapCacheManager(),4);
            for(int i=0;i<3;i++) adapter.getBalance("local-token","local-fintech");
            int before=calls.get();
            double[] baseline=measure(30,()->cache.read("balance","same",true,()->adapter.getBalance("local-token","local-fintech")));
            int baselineCalls=calls.get()-before;
            before=calls.get();
            double[] cached=measure(30,()->cache.read("balance","same",false,()->adapter.getBalance("local-token","local-fintech")));
            int cachedCalls=calls.get()-before;
            assertThat(baselineCalls).isEqualTo(30);
            assertThat(cachedCalls).isZero();
            String report="{\n  \"environment\": \"loopback HTTP, 30ms injected delay, ConcurrentMapCache (not Redis), 30 sequential samples per case\",\n"
                +"  \"baseline\": "+json(baseline,baselineCalls)+",\n  \"warm_cache\": "+json(cached,cachedCalls)+"\n}\n";
            Path output=Path.of("build/reports/external-http-benchmark.json");
            Files.createDirectories(output.getParent());Files.writeString(output,report);
            status.set(500);
            assertThatThrownBy(()->adapter.getBalance("local-token","local-fintech")).isInstanceOf(OpenBankingProviderException.class);
            status.set(200);delay.set(200);
            assertThatThrownBy(()->adapter(uri,Duration.ofMillis(25)).getBalance("local-token","local-fintech"))
                .isInstanceOf(OpenBankingProviderException.class);
        } finally { server.stop(0); }
    }
    private KftcOpenBankingAdapter adapter(URI uri,Duration timeout) {
        return new KftcOpenBankingAdapter(new OpenBankingProperties(uri,uri,"client","secret","1234567890",uri,
                "login inquiry","0",Duration.ofSeconds(1),timeout,""));
    }
    static double[] measure(int n,Runnable action) {
        double[] samples=new double[n];
        for(int i=0;i<n;i++) {long begin=System.nanoTime();action.run();samples[i]=(System.nanoTime()-begin)/1e6;}
        return samples;
    }
    static String json(double[] samples,int calls) {
        double sum=Arrays.stream(samples).sum();Arrays.sort(samples);
        return String.format(Locale.ROOT,"{\"mean_ms\":%.4f,\"p95_ms\":%.4f,\"throughput_per_second\":%.2f,\"errors\":0,\"provider_calls\":%d}",
            sum/samples.length,samples[(int)Math.ceil(samples.length*.95)-1],samples.length*1000/sum,calls);
    }
}
