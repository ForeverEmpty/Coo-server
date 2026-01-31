package org.foreverempty.cooauth;

import org.foreverempty.cooauth.entity.User;
import org.foreverempty.cooauth.es.document.UserDoc;
import org.foreverempty.cooauth.es.repository.UserSearchRepository;
import org.foreverempty.cooauth.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class EsDataSyncTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserSearchRepository userSearchRepository;

    @Test
    public void syncAllUsersToEs() {
        System.out.println(">>>> 开始全量数据同步：MySQL -> ES");

        // 1. 从 MySQL 查询所有用户（如果数据量超大，建议使用分页查询）
        List<User> allUsers = userMapper.selectList(null);

        if (allUsers == null || allUsers.isEmpty()) {
            System.out.println(">>>> MySQL 中没有发现用户数据，同步终止。");
            return;
        }

        System.out.println(">>>> 发现数据量: " + allUsers.size());

        // 2. 将 MySQL 实体转换为 ES 文档实体
        List<UserDoc> docList = allUsers.stream().map(user -> {
            UserDoc doc = new UserDoc();
            // 注意：必须确保 ID 转为 String
            doc.setId(user.getId().toString());
            doc.setUsername(user.getUsername());
            doc.setNickname(user.getNickname());
            doc.setAvatar(user.getAvatar());
            return doc;
        }).collect(Collectors.toList());

        // 3. 调用 Repository 批量保存
        // saveAll 会自动处理新增或覆盖更新
        userSearchRepository.saveAll(docList);

        System.out.println(">>>> 全量同步完成！成功导入 " + docList.size() + " 条数据。");
    }
}
