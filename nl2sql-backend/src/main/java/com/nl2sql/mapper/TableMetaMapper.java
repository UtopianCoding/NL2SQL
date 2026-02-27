package com.nl2sql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nl2sql.model.entity.TableMeta;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TableMetaMapper extends BaseMapper<TableMeta> {
}
