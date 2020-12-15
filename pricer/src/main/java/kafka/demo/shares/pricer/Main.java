package kafka.demo.shares.pricer;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class.getName());

    public static void main(final String[] args) throws Exception {
        PricerConfig config = PricerConfig.fromEnv();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "pricer");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Float().getClass());

        if (config.getTrustStorePassword() != null && config.getTrustStorePath() != null)   {
            log.info("Configuring truststore");
            props.put("security.protocol", "SSL");
            props.put("ssl.truststore.type", "PKCS12");
            props.put("ssl.truststore.password", config.getTrustStorePassword());
            props.put("ssl.truststore.location", config.getTrustStorePath());
        }

        if (config.getKeyStorePassword() != null && config.getKeyStorePath() != null)   {
            log.info("Configuring keystore");
            props.put("security.protocol", "SSL");
            props.put("ssl.keystore.type", "PKCS12");
            props.put("ssl.keystore.password", config.getKeyStorePassword());
            props.put("ssl.keystore.location", config.getKeyStorePath());
        }

        if (config.getUsername() != null && config.getPassword() != null)   {
            props.put("sasl.mechanism","SCRAM-SHA-512");
            props.put("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + config.getUsername() + "\" password=\"" + config.getPassword() + "\";");

            if (props.get("security.protocol") != null && props.get("security.protocol").equals("SSL"))  {
                props.put("security.protocol","SASL_SSL");
            } else {
                props.put("security.protocol","SASL_PLAINTEXT");
            }
        }

        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, Integer> positions = builder.table(config.getLeftSourceTopic(), Consumed.with(Serdes.String(), Serdes.Integer()));
        KTable<String, Float> prices = builder.table(config.getRightSourceTopic(), Consumed.with(Serdes.String(), Serdes.Float()));

        positions.leftJoin(prices, (leftValue, rightValue) -> leftValue * rightValue)
                .toStream()
                .to(config.getTargetTopic());

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
    }
}
