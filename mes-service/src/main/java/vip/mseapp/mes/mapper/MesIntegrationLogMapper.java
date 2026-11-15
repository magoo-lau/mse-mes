package vip.mseapp.mes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mseapp.mes.entity.MesIntegrationLog;

/** 系统集成同步日志 Mapper */
@Mapper
public interface MesIntegrationLogMapper extends BaseMapper<MesIntegrationLog> {
}
