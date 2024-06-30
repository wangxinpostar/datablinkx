package com.datalinkx.copilot.llm;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.datalinkx.copilot.bean.ChunkResult;
import com.datalinkx.copilot.client.OllamaClient;
import com.datalinkx.copilot.client.request.EmbeddingReq;
import com.datalinkx.copilot.client.response.EmbeddingResult;
import com.datalinkx.copilot.vector.ElasticSearchVectorStorage;
import com.datalinkx.copilot.vector.VectorStorage;
import com.datalinkx.copilot.vector.VectorStorageImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Slf4j
@Configuration
public class LoadStartup implements InitializingBean {

    @Autowired
    VectorStorageImpl vectorStorage;

    @Autowired
    OllamaClient ollamaClient;

    @Value("${llm.embedding}")
    String embeddingModel;

    @Value("${llm.vector:elasticsearch}")
    String vectorEngine;


    public void startup(){
        VectorStorage vectorStorage = this.vectorStorage.vectorStorageEngine.get(vectorEngine);
        String collectionName = vectorStorage.getCollectionName();
        //向量维度固定768，根据选择的向量Embedding模型的维度确定最终维度
        // 这里因为选择shaw/dmeta-embedding-zh的Embedding模型，维度是768，所以固定为该值
        vectorStorage.initCollection(collectionName,768);
        log.info("init collection success.");
    }

    public List<ChunkResult> segmentCutting(String docId){
        String path= "data/" + docId + ".txt";
        log.info("start chunk---> docId:{},path:{}", docId, path);
        ClassPathResource classPathResource = new ClassPathResource(path);
        try {
            String txt = IoUtil.read(classPathResource.getInputStream(), StandardCharsets.UTF_8);
            //按固定字数分割,256
            String[] lines = StrUtil.split(txt,256);
            log.info("chunk size:{}", ArrayUtil.length(lines));
            List<ChunkResult> results = new ArrayList<>();
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (String line:lines) {
                ChunkResult chunkResult = new ChunkResult();
                chunkResult.setDocId(docId);
                chunkResult.setContent(line);
                chunkResult.setChunkId(atomicInteger.incrementAndGet());
                results.add(chunkResult);
            }
            return results;
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return new ArrayList<>();
    }

    // TODO 每次初始化文本要删除旧数据
    @Override
    public void afterPropertiesSet() {
        // 初始化向量
        this.startup();
        // 加载本地知识库
        List<ChunkResult> chunkResults = this.segmentCutting("001");

        VectorStorage vectorStorage = this.vectorStorage.vectorStorageEngine.get(vectorEngine);
        // embedding
        EmbeddingReq embeddingReq = EmbeddingReq.builder().model(embeddingModel).build();
        for (ChunkResult chunkResult : chunkResults) {
            try {
                embeddingReq.setPrompt(chunkResult.getContent());
                EmbeddingResult embeddingResult = ollamaClient.embedding(embeddingReq);
                // store vector
                String collection = vectorStorage.getCollectionName();
                embeddingResult.setContent(chunkResult.getContent());
                vectorStorage.store(collection, embeddingResult);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }
}
