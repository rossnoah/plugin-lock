(function () {
  function copyText(value) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(value);
    }

    return new Promise(function (resolve, reject) {
      var textarea = document.createElement("textarea");
      textarea.value = value;
      textarea.setAttribute("readonly", "");
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      document.body.appendChild(textarea);
      textarea.select();

      try {
        document.execCommand("copy") ? resolve() : reject(new Error("copy failed"));
      } catch (error) {
        reject(error);
      } finally {
        document.body.removeChild(textarea);
      }
    });
  }

  document.querySelectorAll("pre.command").forEach(function (block) {
    var code = block.querySelector("code");
    if (!code) {
      return;
    }

    var button = document.createElement("button");
    button.type = "button";
    button.className = "copy-command";
    button.textContent = "Copy";
    button.setAttribute("aria-label", "Copy command");

    button.addEventListener("click", function () {
      copyText(code.textContent.trimEnd()).then(function () {
        button.textContent = "Copied";
        button.classList.add("copied");
        button.blur();
        window.setTimeout(function () {
          button.textContent = "Copy";
          button.classList.remove("copied");
        }, 1400);
      }).catch(function () {
        button.textContent = "Failed";
        button.blur();
        window.setTimeout(function () {
          button.textContent = "Copy";
        }, 1400);
      });
    });

    block.appendChild(button);
  });
}());
