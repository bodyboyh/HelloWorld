<%--
  Created by IntelliJ IDEA.
  User: 沫~晓飞
  Date: 2019/7/1
  Time: 11:11
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java"   pageEncoding="UTF-8" %>
<html>
<head>
    <title>Title</title>
   <%-- <script src="http://libs.baidu.com/jquery/2.1.4/jquery.min.js"></script>--%>
    <script  src="js/jquery-3.0.0.js"></script>
    <script type="text/javascript">
        <!--页面加载完做的事情-->
        $(function () {
            $("#translate").click(function () {
                debugger
                var  source = $("#source").val();
                $.ajax({
                    url:"tranlate",//请求的地址
                    data:"source="+source,//请求的数据
                    success:function (data) {//回调函数
                        //将返回的结果送给target
                        $("#target").val(data);
                    }
                    });
            })
        })
    </script>
</head>
<body>
    测试的情况啊小兄弟
    <div  style="width: 300px;height: 100px;color: blue;float: left">
        <textarea  rows="10"  cols="30"  id="source">

        </textarea>

    </div>
    <!--放置按钮-->
    <div  style="float: left">
        <input  type="button"  value="翻译" id="translate"/>
    </div>
    <div  style="width: 300px;height: 100px;color: red;float: left">
        <textarea  rows="10"  cols="30"  id="target">

        </textarea>

    </div>
</body>
</html>
