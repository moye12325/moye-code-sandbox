package com.moye.moyecodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 执行消耗的内存
     */
    private Long memory;

    /**
     * 执行消耗的时间
     */
    private Long time;
}
