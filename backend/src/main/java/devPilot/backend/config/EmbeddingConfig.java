package devPilot.backend.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        
        //to pick file from local source
        model.setTokenizerResource(new ClassPathResource("onnx/tokenizer.json"));
        model.setModelResource(new ClassPathResource("onnx/model.onnx"));
        
        return model;
    }
}