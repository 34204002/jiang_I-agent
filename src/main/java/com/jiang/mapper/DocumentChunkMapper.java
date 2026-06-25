package com.jiang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiang.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档分片 Mapper
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
