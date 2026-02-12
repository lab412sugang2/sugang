//alert 레이어
$.alert = function (message, callback) {
    var $messageBox = $("#message", window.parent.document);
    $messageBox.find("#msgCont").html(message);
    $messageBox.dialog({
        width: 402,
        height: 200,
        position: {my: "center", at: "center", of: window.top},
        modal: true,
        buttons: [
            {
                text: "확인",
                "class": 'btn_pop_save',
                click: function() {
                    $(this).dialog("close");
                    if (callback != undefined) {
                        eval(callback);
                    }
                },
                keydown: function(key) {
                	if (key.keyCode == 13) {
                		$messageBox.dialog("close");
                        if (callback != undefined) {
                            eval(callback);
                        }
                	} else if (key.keyCode == 27) {
                		return false;
                	}
                }
            }
        ]
    });
};

//confirm 레이어
$.confirm = function (message, okCallback, cancelCallback) {
    var $messageBox = $("#message", window.parent.document);
    $messageBox.find("#msgCont").html(message);
    $messageBox.dialog({
        width: 402,
        height: 250,
        position: {my: "center", at: "center", of: window.top},
        modal: true,
        buttons: [
            {
                text: "확인",
                "class": 'btn_pop_save',
                click: function() {
                    $(this).dialog("close");
                    if (okCallback != undefined) {
                        eval(okCallback);
                    }
                },
                keydown: function(key) {
                	if (key.keyCode == 13) {
                		$messageBox.dialog("close");
                        if (okCallback != undefined) {
                            eval(okCallback);
                        }
                	} else if (key.keyCode == 27) {
                		return false;
                	}
                }
            },
            {
                text: "취소",
                click: function() {
                    $(this).dialog("close");
                    if (cancelCallback != undefined) {
                        eval(cancelCallback);
                    }
                }
            }
        ]
    });
};

//submit
/**
 * @desc      : submit을 보낸다.
 * @comment   : 사용자로 부터 받은 url 의 경로로 form을 만들어 submit한다.
 * @param     : url    :
 * @param     : params : json type의 param
 * @create    : 2014.10.22
 * @author    : jsKim
 */
$.submit = function (url,params) {
    if (!url) {
        alert("url은 필수값입니다.");
        return false;
    }

    var elements = "";
    $.each(params,function(key,value){
        elements += "<input type='hidden' name='" + key + "' value='" + value + "'>";
    });

    $("<form method='post' action='" + url + "'>" + elements + "</form>").appendTo("body").submit().remove();
};

//addOptions
/**
 * @desc      : select tag의 option을 추가해준다.
 * @comment   :
 * @param     : options  - selectedVal : 초기 select의 선택값
 *                       - callback    : select를 생성후 처리할 callback funtion명
 *                       - value       : select의 option의 value
 *                       - text        : select의 option의 text  <option value="1">text</option>
 *                       - dataList    : 조회된 data
 * @create    : 2014.10.16
 * @author    : jsKim
 */
$.fn.addOptions = function(options) {
    $(this).find("option").remove();

    if (!options) {
        alert("parameter가 없습니다.");
        return false;
    }

    var placeholder = $(this).data("placeholder");
    if (placeholder) {
        $(this).append($("<option>", {value: "", text: placeholder}));
    }

    for (var index = 0; index < options.dataList.length; index++) {
        $(this).append($("<option>", {value: options.dataList[index][options.value], text : options.dataList[index][options.text]}));
    }

    if (options.selectedVal) {
        $(this).selected(options.selectedVal);
    } else {
        $(this).trigger("chosen:updated");
    }

    if (options.callback) {
        eval(options.callback)();
    }
};

//seleced
/**
 * @desc      : 해당 값으로 selected 해준다.
 * @param     : val - select 값
 * @author    : jsKim
 */
$.fn.selected = function(val, parentWindow) {
    if (val != undefined && val != null) {
        if (parentWindow) {
            parentWindow.$(this).val(val).trigger("chosen:updated").trigger("change");
        } else {
            $(this).val(val).trigger("chosen:updated");
        }
    }
    return $(this);
};

