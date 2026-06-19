package com.jiang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiang.entity.AgentConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 全局配置 Mapper
 */
@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfig> {
}
