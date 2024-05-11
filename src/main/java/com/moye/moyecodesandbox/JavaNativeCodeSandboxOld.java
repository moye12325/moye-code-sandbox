package com.moye.moyecodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.moye.moyecodesandbox.model.ExecuteCodeRequest;
import com.moye.moyecodesandbox.model.ExecuteCodeResponse;
import com.moye.moyecodesandbox.model.ExecuteMessage;
import com.moye.moyecodesandbox.model.JudgeInfo;
import com.moye.moyecodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final Object SECURITY_MANAGER_PATH = "src/main/resources/security";
    private static final Object SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    private static final long TIME_OUT = 5000L;


    public static void main(String[] args) {
        JavaNativeCodeSandboxOld javaNativeCodeSandbox = new JavaNativeCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4", "5 6", "7 8", "9 10"));
//        String code = ResourceUtil.readStr("test/simple/Main.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testCode/unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);

    }

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

//        1 把用户的代码保存为文件
//        2 编译代码，得到 class 文件
//        3 执行代码，得到输出结果
//        4 收集整理输出结果
//        5 文件清理，释放空间
//        6 错误处理，提升程序健壮性

        // 提高安全性
//        System.setSecurityManager(new SecurityManager());

        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();

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

        //编译代码
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println("executeMessage = " + executeMessage);
        } catch (IOException ex) {
            getErrorResponse(ex);
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            getErrorResponse(ex);
            throw new RuntimeException(ex);
        }

        // 3 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {

            // 设置运行内存限制
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);

                // 设置运行超时限制
                new Thread(()->{
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
//        4 收集整理输出结果
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

//        // 5 文件清理，释放空间
//        if (userCodeFile.getParentFile().exists()) {
//            boolean del = FileUtil.del(userCodeParentPath);
//            System.out.println("删除" + (del ? "成功" : "失败"));
//        }

        return executeCodeResponse;
    }


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
