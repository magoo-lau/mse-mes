package vip.mseapp.mes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mseapp.mes.ai.MesAiAgentMap;

/** AI 场景 → 智能体映射 Mapper（mapper 包统一被 @MapperScan 扫描） */
@Mapper
public interface MesAiAgentMapMapper extends BaseMapper<MesAiAgentMap> {
}
