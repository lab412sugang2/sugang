
import os

file_path = "단국대학교 수강신청 시스템_files/findTkcrsApl.html"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# The script start tag to look for (the last one, or specific one)
# I know the script I want to replace starts with $(document).ready and is near the end.
# I'll replace everything from line 1735 <script> to line 1886 </script>

start_marker = "  <script>\n    $(document).ready(function () {"
end_marker = "  </script>\n</body>"

# Find the start index
start_idx = content.rfind("  <script>\n    $(document).ready(function () {")
if start_idx == -1:
    print("Could not find start marker")
    # try slightly different whitespace
    start_idx = content.rfind("<script>\n    $(document).ready(function () {")

if start_idx == -1:
    print("Could not find start marker even with loose check")
    exit(1)

# Find the end index
end_idx = content.find("</script>", start_idx)
if end_idx == -1:
    print("Could not find end marker")
    exit(1)

# Construct new script content
new_script = """<script>
    $(document).ready(function () {
      console.log("Custom script loaded."); // Debugging

      // 1. Initial Cleanup: Removed as per request (static rows removed manually from HTML)
      // $("#tkcrsAplTbl tbody").empty();

      // 2. Click listener for Course Plan Assistant (Add Course)
      $("body").on("click", "#tabs-1 tbody tr", function () {
        console.log("Course clicked.");
        var $this = $(this);
        
        // Extract Data
        var subjId = $this.find("input[name='subjId']").val();
        var subjNm = $this.find("input[name='subjNm']").val();
        var dvclsNb = $this.find("input[name='dvclsNb']").val();
        
        // Visual cells
        var credit = $this.find("td").eq(3).text().trim();
        var prof = $this.find("td").eq(4).text().trim();
        var schedule = $this.find("td").eq(5).text().trim();
        var campus = "죽전"; 
        var type = "대면수업"; 

        console.log("Selected:", subjId, subjNm, schedule);

        // Check for duplicates
        var isDuplicate = false;
        $("#tkcrsAplTbl tbody tr").each(function() {
             if ($(this).data('subjid') == subjId) {
                 isDuplicate = true;
                 return false; 
             }
        });

        if (isDuplicate) {
          alert("이미 신청된 과목입니다.");
          return;
        }

        // Exact HTML Structure
        var newRow1 = "<tr data-subjid='" + subjId + "' data-schedule='" + schedule + "'>" +
          "<td rowspan='2'>" + 
            "<a href='javascript:void(0);' class='btn btn_inner btn_del'>" + 
              "<span class='btn_txt'>삭제</span><!-- 삭제 -->" + 
            "</a>" + 
            "\\n" +
            "<!-- 20210906 kdh 공유혁신대학 관리항목 과목만 삭제버튼이 보이게 수정 -->\\n" +
            "<!-- 대학원 정정기간에 오픈하므로 대학원은 삭제버튼 생성  -->\\n" +
            "\\n" +
            "<!--주석 끝  20210906 kdh 공유혁신대학 관리항목 과목만 삭제버튼이 보이게 수정 -->\\n" +
            "\\n" +
            "<input type='hidden' name='idx' value='1'>" + 
            "<input type='hidden' name='opOrgid' value='2000000989'>" +
            "<input type='hidden' name='subjId' value='" + subjId + "'>" +
            "<input type='hidden' name='subjNm' value='" + subjNm + "'>" +
            "<input type='hidden' name='dvclsNb' value='" + dvclsNb + "'>" +
            "<input type='hidden' name='webAplDelPsblYn' value='1'>" +
            "<input type='hidden' name='tkcrsWdPsblYn' value='1'>" +
            "<input type='hidden' name='sznDtlWokId' value=''>" +
            "<input type='hidden' name='detMngtDscCd' value=''>" +
            "<!-- 20210906 kdh 개설교과목 상세 관리항목추가 - 혁신공유대학과목을 구분하기 위함. 삭제버튼 컨트롤 -->" +
          "</td>" +
          "<td>" + campus + "</td>" +
          "<td>" + subjId + "</td>" +
          "<td>" + dvclsNb + "</td>" +
          "<td class='ta_l'>" + subjNm + "</td>" +
          "<td>" + credit + "</td>" +
          "<td>" + prof + "</td>" +
          "<td></td>" + 
          "<td>" + type + "</td>" +
          "</tr>";
          
        var newRow2 = "<tr data-subjid='" + subjId + "'>" + 
          "<td colspan='8' class='ta_l'>" + schedule + "</td>" + 
          "</tr>";

        $("#tkcrsAplTbl tbody").append(newRow1 + newRow2);

        // Color Timetable
        colorTimetable(schedule, true);
      });

      // 3. Delete button listener
      $("#tkcrsAplTbl tbody").on("click", ".btn_del", function () {
        console.log("Delete clicked.");
        var $row1 = $(this).closest("tr");
        var schedule = $row1.data("schedule");
        var $row2 = $row1.next("tr"); 

        $row1.remove();
        $row2.remove();

        // Remove color from Timetable
        colorTimetable(schedule, false);
      });

      // Helper function to color timetable
      function colorTimetable(scheduleStr, isAdd) {
        if (!scheduleStr) return;
        var cleanStr = scheduleStr.replace(/\([^\)]*\)/g, ''); 
        var parts = cleanStr.split('/');
        parts.forEach(function (part) {
          part = part.trim();
          if (part.length === 0) return;
          var dayChar = part.charAt(0);
          var periodsStr = part.substring(1);
          var periods = periodsStr.split(',');
          var dayIndex = -1;
          switch (dayChar) {
            case '월': dayIndex = 1; break;
            case '화': dayIndex = 2; break;
            case '수': dayIndex = 3; break;
            case '목': dayIndex = 4; break;
            case '금': dayIndex = 5; break;
            case '토': dayIndex = 6; break;
          }
          if (dayIndex !== -1) {
            periods.forEach(function (period) {
              var periodNum = parseInt(period);
              if (!isNaN(periodNum)) {
                  var $targetRow = $("#tkcrsAplTmtblTbl tbody tr").eq(periodNum - 1);
                  var $targetCell = $targetRow.find("td").eq(dayIndex);
                  if (isAdd) {
                    $targetCell.css("background-color", "#ffaaaa");
                  } else {
                    $targetCell.css("background-color", "");
                  }
              }
            });
          }
        });
      }
    });"""

# Replace
new_content = content[:start_idx] + new_script + content[end_idx + 9:]

# Write back
with open(file_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("File updated successfully")
