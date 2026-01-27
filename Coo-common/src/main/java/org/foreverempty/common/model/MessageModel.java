package org.foreverempty.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageModel<T> {
    private String type;
    private String sequence;
    private T data;
}
