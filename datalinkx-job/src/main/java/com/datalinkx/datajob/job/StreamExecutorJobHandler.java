package com.datalinkx.datajob.job;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.datalinkx.common.utils.ObjectUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StreamExecutorJobHandler extends ExecutorJobHandler {

    public String execute(String jobId, String reader, String writer, Map<String, String> otherSetting) throws Exception {
        return super.execute(jobId, reader, writer, otherSetting);
    }


    @Override
    public String generateFlinkCmd(String jobId, String jobJsonFile, Map<String, String> otherSetting) {
        String javaHome = System.getenv("JAVA_HOME");
        String os = System.getProperty("os.name").toLowerCase();

        String executeCmd = String.format(
                "%s -cp %s com.dtstack.flinkx.launcher.Launcher -mode standalone -jobid %s" +
                        "  -job %s  -pluginRoot %s -flinkconf %s",
                javaHome + (os.contains("win") ? "\\bin\\java" : "/bin/java"),
                flinkXHomePath + (os.contains("win") ? "lib\\*" : "lib/*"),
                jobId,
                jobJsonFile,
                flinkXHomePath + "syncplugins",
                flinkXHomePath + "flinkconf"
        );


        if (!ObjectUtils.isEmpty(otherSetting.get("savePointPath"))) {
            executeCmd += " -confProp \"{\"flink.checkpoint.interval\":60000}\" ";
            executeCmd = executeCmd + " -s savePointPath " + otherSetting.get("savePointPath");
        }

        return executeCmd;
    }

    @SneakyThrows
    public String generateJobSetting() {
        return "{\n" +
                "            \"restore\": {\n" +
                "                \"isRestore\": true,\n" +
                "                \"isStream\": true\n" +
                "            },\n" +
                "            \"speed\": {\n" +
                "                \"channel\": 1\n" +
                "            }\n" +
                "        }";
    }
}
