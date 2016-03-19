package org.apache.nutch.parse.lq.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

public class MyBatisUtil {
    public static final String MYBATIS_CONFIG                      = "mybatis.config";
    public static final Logger LOG = LoggerFactory.getLogger(MyBatisUtil.class);
    static SqlSessionFactory   sqlSessionFactory                   = null;
	public static   SqlSessionFactory getSessionFactory(Reader reader) {
		return  new SqlSessionFactoryBuilder().build(reader);
	}
    public static Reader getMybatisReader(Configuration conf) throws IOException {
        String stringRules = conf.get(MYBATIS_CONFIG);
        if (stringRules != null) {
            return conf.getConfResourceAsReader(stringRules);
        }
        return null;
    }
    public static synchronized SqlSessionFactory getSqlSessionFactory(Configuration conf) {
        if (sqlSessionFactory != null) {
            return sqlSessionFactory;
        }
        try {
            Reader reader = getMybatisReader(conf);
            sqlSessionFactory = getSessionFactory(reader);
        } catch (IOException e) {
            LOG.error(e.getMessage() + "读取mybatis配置文件异常", e);
        }
        return sqlSessionFactory;
    }
}
