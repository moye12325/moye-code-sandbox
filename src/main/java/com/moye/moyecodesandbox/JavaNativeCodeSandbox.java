package com.moye.moyecodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.moye.moyecodesandbox.model.ExecuteCodeRequest;
import com.moye.moyecodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Java原生沙箱实现
 */
@Component
public class JavaNativeCodeSandbox extends JavaSandboxTemplate{

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
