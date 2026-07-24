package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.JobRun;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * job_run 表访问接口（运维表）。
 */
public interface JobRunMapper {

    /** 阶段开始：插入或重置该阶段记录为 RUNNING。 */
    int upsertRunning(JobRun jobRun);

    /** 阶段结束：更新状态/统计/错误。 */
    int finish(JobRun jobRun);

    /** 查询某市场最近一次指定阶段成功的记录（数据新鲜度）。 */
    JobRun findLatestSuccess(@Param("market") String market, @Param("phase") String phase);

    /** 每个市场最近一次运行的 run_id（用于运行状态展示）。 */
    List<JobRun> findLatestRunPerMarket();

    /** 某次运行的全部阶段记录。 */
    List<JobRun> findByRunId(@Param("runId") String runId);
}
