package com.jiang.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;
    /** 当前页码 */
    private int page;
    /** 每页大小 */
    private int size;
    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }
}
