package com.moye.moyecodesandbox;


import com.moye.moyecodesandbox.model.ExecuteCodeRequest;
import com.moye.moyecodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
