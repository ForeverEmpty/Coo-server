package org.foreverempty.cooauth.es.repository;

import org.foreverempty.cooauth.es.document.UserDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserDoc, String> {
    List<UserDoc> findByNickname(String nickname);

    UserDoc findByUsername(String username);

    List<UserDoc> findByNicknameOrUsername(String nickname, String username);

    Page<UserDoc> findByUsernameContainingOrNicknameMatches(String username, String nickname, Pageable pageable);
}
