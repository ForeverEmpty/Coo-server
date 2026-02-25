package org.foreverempty.cooauth.es;

import org.foreverempty.common.handler.AbstractCanalHandler;
import org.foreverempty.cooauth.es.document.UserDoc;
import org.foreverempty.cooauth.es.repository.UserSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.alibaba.otter.canal.protocol.CanalEntry.*;

import java.util.List;

@Component
public class UserCanalSyncTask extends AbstractCanalHandler implements CommandLineRunner {

    @Autowired
    private UserSearchRepository userSearchRepository;

    @Override
    protected String getDestination() {
        return "coo_chat\\.u_user";
    }

    @Override
    public void run(String... args) {
        this.start();
    }

    @Override
    protected void processRowData(EventType eventType, List<Column> beforeColumns, List<Column> afterColumns) {
        if (eventType == EventType.DELETE) {
            String id = getVal(beforeColumns, "id");
            userSearchRepository.deleteById(id);
        } else {
            UserDoc doc = new UserDoc();
            doc.setId(getVal(afterColumns, "id"));
            doc.setUsername(getVal(afterColumns, "username"));
            doc.setNickname(getVal(afterColumns, "nickname"));
            doc.setAvatar(getVal(afterColumns, "avatar"));
            userSearchRepository.save(doc);
        }
    }
}
