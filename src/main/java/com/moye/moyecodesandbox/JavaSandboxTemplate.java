package com.moye.moyecodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.moye.moyecodesandbox.model.ExecuteCodeRequest;
import com.moye.moyecodesandbox.model.ExecuteCodeResponse;
import com.moye.moyecodesandbox.model.ExecuteMessage;
import com.moye.moyecodesandbox.model.JudgeInfo;
import com.moye.moyecodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class JavaSandboxTemplate implements CodeSandbox {


    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final Object SECURITY_MANAGER_PATH = "src/main/resources/security";
    private static final Object SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    private static final long TIME_OUT = 5000L;
    private static final boolean FIRST_INIT = true;


    /**
     * 1.把用户的代码保存为文件
     *
     * @param code
     * @return
     */
    public File savaCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        System.out.println("globalCodePathName = " + globalCodePathName);
        // 判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2. 编译代码
     *
     * @param codeFile
     * @return
     */
    public ExecuteMessage compileFile(File codeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", codeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println("executeMessage = " + executeMessage);
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * 3 执行代码，得到输出结果
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {

        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {

            // 设置运行内存限制
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 设置运行超时限制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断程序");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println("executeMessage = " + executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException ex) {
                getErrorResponse(ex);
                throw new RuntimeException(ex);
            } catch (Exception ex) {
                getErrorResponse(ex);
                throw new RuntimeException(ex);
            }
        }
        return executeMessageList;
    }

    /**
     * 4 收集整理输出结果
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误信息
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            long time = executeMessage.getTime();
            if (time > maxTime) {
                maxTime = Math.max(maxTime, time);
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }

        executeCodeResponse.setOutputList(outputList);

        JudgeInfo judgeInfo = new JudgeInfo();
        //借助第三方库获取内存
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5 文件清理，释放空间
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();

        //1. 把用户的代码保存为文件
        File codeFile = savaCodeToFile(code);

        //2. 编译代码
        ExecuteMessage compileFileExecuteMessage = compileFile(codeFile);
        System.out.println("compileFileExecuteMessage = " + compileFileExecuteMessage);

        // 3 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(codeFile, inputList);

        // 4 收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        // 5 文件清理，释放空间
        boolean deleteFile = deleteFile(codeFile);
        if (!deleteFile) {
            log.error("deleteFile error,userCodeFilePath: {}", codeFile.getAbsolutePath());
        }
        return outputResponse;
    }


    /**
     * 6. 获取错误响应
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        // 6 错误处理，提升程序健壮性
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
